package com.devpilot.gateway.orca;

import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thin, argv-only client for the Orca CLI that the gateway is allowed to drive.
 *
 * <p>Verified against Orca 1.4.263: a supervised worker is started with
 * {@code orchestration worker-start}, observed with {@code worker-show},
 * read with {@code worker-read}, cancelled with {@code worker-stop} and
 * released with {@code worker-release}. Orca only accepts workspaces it knows,
 * so a plain git worktree is registered as a folder workspace first.
 */
@Component
public class OrcaCli {

    private static final Logger log = LoggerFactory.getLogger(OrcaCli.class);
    private static final int MAX_CAPTURE_BYTES = 5 * 1024 * 1024;

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    /** Exact workspace selector per path; `path:` alone is ambiguous once two
     * worktrees of the same repository are registered. */
    private final Map<Path, String> workspaceSelectors = new ConcurrentHashMap<>();
    private volatile String senderHandle;

    public OrcaCli(GatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public WorkerStart startWorker(Path workspace, Path specFile, String agentId,
                                   String taskTitle, String model) {
        String selector = ensureWorkspace(workspace);
        List<String> args = new ArrayList<>(List.of(
                "orchestration", "worker-start",
                "--spec-file", specFile.toString(),
                "--worktree", selector,
                "--agent", agentId,
                "--task-title", taskTitle,
                "--json"));
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }
        JsonNode result = startWithSender(workspace, args);
        String dispatchId = text(result, "dispatchId");
        if (dispatchId == null || dispatchId.isBlank()) {
            throw new ValidationException("orca worker-start returned no dispatchId");
        }
        log.info("started Orca worker {} for workspace {}", dispatchId, workspace);
        return new WorkerStart(dispatchId, text(result, "workspace"), text(result, "state"));
    }

    /**
     * Orca refuses an orchestration mutation without a calling terminal. The
     * gateway has no tty, so it keeps one closed control terminal handle as its
     * sender identity and recreates it if Orca stops accepting it.
     */
    private JsonNode startWithSender(Path workspace, List<String> baseArgs) {
        try {
            return requireOk(exec(workspace, withSender(baseArgs, senderHandle()),
                    properties.getWorkerStartTimeout()));
        } catch (ValidationException e) {
            String message = String.valueOf(e.getMessage());
            if (!message.contains("sender") && !message.contains("no_active_sender_terminal")) {
                throw e;
            }
            log.info("sender terminal rejected, recreating control terminal");
            resetSenderHandle();
            return requireOk(exec(workspace, withSender(baseArgs, senderHandle()),
                    properties.getWorkerStartTimeout()));
        }
    }

    private List<String> withSender(List<String> args, String handle) {
        List<String> full = new ArrayList<>(args);
        full.add("--from");
        full.add(handle);
        return full;
    }

    private String senderHandle() {
        String cached = senderHandle;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (senderHandle != null) {
                return senderHandle;
            }
            Path controlDir = Paths.get(properties.getStateDir()).toAbsolutePath()
                    .resolve("control");
            try {
                Files.createDirectories(controlDir);
            } catch (IOException e) {
                throw new ValidationException("cannot create control workspace: " + e.getMessage());
            }
            String controlSelector = ensureWorkspace(controlDir);
            JsonNode created = requireOk(exec(null, List.of(
                            "terminal", "create", "--worktree", controlSelector, "--json"),
                    Duration.ofSeconds(60)));
            String handle = created.path("terminal").path("handle").asText(null);
            if (handle == null || handle.isBlank()) {
                throw new ValidationException("orca terminal create returned no handle");
            }
            // The handle stays valid as a mailbox identity after the terminal closes.
            exec(null, List.of("terminal", "close", "--terminal", handle), Duration.ofSeconds(30));
            hideWorkspace(controlDir);
            senderHandle = handle;
            log.info("using Orca control terminal identity {}", handle);
            return handle;
        }
    }

    private void resetSenderHandle() {
        synchronized (this) {
            senderHandle = null;
        }
    }

    public WorkerView show(String dispatchId) {
        JsonNode result = requireOk(exec(null, List.of(
                "orchestration", "worker-show", "--dispatch", dispatchId, "--json"),
                Duration.ofSeconds(30)));
        return new WorkerView(text(result, "state"), text(result, "stage"),
                text(result, "activity"), terminal(text(result, "state")));
    }

    public WorkerRead read(String dispatchId, String cursor, int limit) {
        List<String> args = new ArrayList<>(List.of(
                "orchestration", "worker-read", "--dispatch", dispatchId,
                "--limit", String.valueOf(limit), "--json"));
        if (cursor != null && !cursor.isBlank()) {
            args.add("--cursor");
            args.add(cursor);
        }
        JsonNode result = requireOk(exec(null, args, Duration.ofSeconds(30)));
        JsonNode transcript = result.path("transcript");
        List<Message> messages = new ArrayList<>();
        JsonNode rows = transcript.path("messages");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String role = row.path("role").asText("");
                String id = row.path("id").asText(null);
                StringBuilder text = new StringBuilder();
                JsonNode blocks = row.path("blocks");
                if (blocks.isArray()) {
                    for (JsonNode block : blocks) {
                        if (block.hasNonNull("text")) {
                            if (!text.isEmpty()) {
                                text.append('\n');
                            }
                            text.append(block.get("text").asText());
                        }
                    }
                }
                if (!text.isEmpty()) {
                    messages.add(new Message(id, role, text.toString()));
                }
            }
        }
        JsonNode projection = result.path("projection");
        List<String> attention = new ArrayList<>();
        JsonNode categories = projection.path("attention").path("categories");
        if (categories.isArray()) {
            categories.forEach(node -> attention.add(node.asText()));
        }
        return new WorkerRead(messages,
                transcript.hasNonNull("nextCursor") ? transcript.get("nextCursor").asText()
                        : text(result, "cursor"),
                projection.path("outcome").asText(null),
                attention,
                projection.path("attention").path("requiresAction").asBoolean(false));
    }

    public void stop(String dispatchId) {
        try {
            exec(null, List.of("orchestration", "worker-stop", "--dispatch", dispatchId),
                    Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.warn("cannot stop Orca worker {}: {}", dispatchId, e.getMessage());
        }
    }

    public void release(String dispatchId) {
        try {
            exec(null, List.of("orchestration", "worker-release", "--dispatch", dispatchId),
                    Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.debug("cannot release Orca worker {}: {}", dispatchId, e.getMessage());
        }
    }

    public void hideWorkspace(Path workspace) {
        if (!properties.isHideWorkspaceWhenDone()) {
            return;
        }
        String selector = workspaceSelectors.getOrDefault(
                workspace.toAbsolutePath().normalize(), "path:" + workspace);
        try {
            exec(null, List.of("repo", "hide", "--repo", repoSelector(selector)),
                    Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.debug("cannot hide Orca workspace {}: {}", workspace, e.getMessage());
        }
    }

    /**
     * Orca refuses to launch an agent outside a workspace it knows. A devpilot
     * task workspace is a git worktree Orca never created, so register it as a
     * folder workspace; the registration is idempotent per path in this process.
     */
    private synchronized String ensureWorkspace(Path workspace) {
        Path target = workspace.toAbsolutePath().normalize();
        String cached = workspaceSelectors.get(target);
        if (cached != null) {
            return cached;
        }
        // `worktree current` also succeeds for a subdirectory of a larger
        // workspace (it resolves to the parent), so the exact path must match.
        RawResult current = exec(target, List.of("worktree", "current", "--json"),
                Duration.ofSeconds(30));
        String exact = current.exitCode() == 0 ? exactSelector(current.stdout(), target) : null;
        if (exact != null) {
            workspaceSelectors.put(target, exact);
            return exact;
        }
        if (!properties.isRegisterWorkspace()) {
            throw new ValidationException(
                    "workspace is not known to Orca and automatic registration is disabled: " + target);
        }
        exec(target.getParent(), List.of(
                "repo", "add", "--kind", "folder", "--path", target.toString(), "--json"),
                Duration.ofSeconds(60));
        log.info("registered Orca folder workspace {}", target);
        RawResult registered = exec(target, List.of("worktree", "current", "--json"),
                Duration.ofSeconds(30));
        String selector = registered.exitCode() == 0
                ? exactSelector(registered.stdout(), target) : null;
        if (selector == null) {
            log.warn("cannot resolve exact Orca selector for {}; falling back to path:",
                    target);
            selector = "path:" + target;
        }
        workspaceSelectors.put(target, selector);
        return selector;
    }

    /**
     * Returns `id:<repo>::<path>` for the exact worktree, or null when
     * `worktree current` resolved to a different (parent) workspace.
     */
    private String exactSelector(String stdout, Path target) {
        try {
            JsonNode worktree = objectMapper.readTree(stdout).path("result").path("worktree");
            String path = worktree.path("path").asText("");
            String id = worktree.path("id").asText("");
            if (path.isBlank() || id.isBlank()) {
                return null;
            }
            if (!Path.of(path).toAbsolutePath().normalize().equals(target)) {
                return null;
            }
            return "id:" + id;
        } catch (IOException e) {
            return null;
        }
    }

    private static String repoSelector(String workspaceSelector) {
        if (!workspaceSelector.startsWith("id:")) {
            return workspaceSelector;
        }
        String id = workspaceSelector.substring(3);
        int split = id.indexOf("::");
        return split < 0 ? workspaceSelector : "id:" + id.substring(0, split);
    }

    private JsonNode requireOk(RawResult result) {
        if (result.exitCode() != 0) {
            throw new ValidationException("orca command failed (exit " + result.exitCode() + "): "
                    + abbreviate(result.stderr().isBlank() ? result.stdout() : result.stderr()));
        }
        try {
            JsonNode root = objectMapper.readTree(result.stdout());
            if (root.has("ok") && !root.get("ok").asBoolean()) {
                String message = root.path("error").path("message").asText("orca command failed");
                throw new ValidationException("orca command failed: " + message);
            }
            return root.has("result") ? root.get("result") : root;
        } catch (IOException e) {
            throw new ValidationException("cannot parse orca output: " + e.getMessage()
                    + " :: " + abbreviate(result.stdout()));
        }
    }

    private RawResult exec(Path cwd, List<String> args, Duration timeout) {
        List<String> argv = new ArrayList<>();
        argv.add(properties.getOrcaBinary());
        argv.addAll(args);
        var builder = new ProcessBuilder(argv).redirectErrorStream(false);
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        builder.environment().put("CI", "true");
        try {
            Process process = builder.start();
            var stdout = CompletableFuture.supplyAsync(() -> readBounded(process.getInputStream()));
            var stderr = CompletableFuture.supplyAsync(() -> readBounded(process.getErrorStream()));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ValidationException("orca CLI timed out after " + timeout);
            }
            return new RawResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException e) {
            throw new ValidationException(
                    "cannot run orca binary '" + properties.getOrcaBinary() + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("interrupted while running orca CLI");
        }
    }

    private String readBounded(InputStream stream) {
        try (stream) {
            byte[] buffer = stream.readNBytes(MAX_CAPTURE_BYTES);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean terminal(String state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case "succeeded", "failed", "cancelled", "stopped" -> true;
            default -> false;
        };
    }

    private static String text(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    public record WorkerStart(String dispatchId, String workspace, String state) {
    }

    public record WorkerView(String state, String stage, String activity, boolean terminal) {
    }

    public record WorkerRead(List<Message> messages, String cursor, String outcome,
                             List<String> attention, boolean requiresAction) {
    }

    public record Message(String id, String role, String text) {
    }

    private record RawResult(int exitCode, String stdout, String stderr) {
    }
}