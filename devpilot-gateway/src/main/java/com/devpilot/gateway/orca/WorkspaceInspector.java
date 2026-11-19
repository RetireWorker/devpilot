package com.devpilot.gateway.orca;

import com.devpilot.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Reads the real state of a task workspace after an agent ran: which files
 * changed, whether they stay inside the allowed scope, and the commit that
 * records them. Git is the source of truth, never the agent's self-report.
 */
@Component
public class WorkspaceInspector {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInspector.class);

    public String head(Path workspace) {
        RawResult result = git(workspace, List.of("rev-parse", "HEAD"), Duration.ofSeconds(30));
        return result.exitCode() == 0 ? result.stdout().strip() : null;
    }

    /**
     * Changed files are the union of the uncommitted working tree and any
     * commits the agent produced on top of {@code baseSha}. Deduplicated and
     * ordered for stable reports.
     */
    public List<String> changedFiles(Path workspace, String baseSha) {
        Set<String> files = new LinkedHashSet<>(statusFiles(workspace));
        if (baseSha != null) {
            String current = head(workspace);
            if (current != null && !current.equals(baseSha)) {
                RawResult diff = git(workspace,
                        List.of("diff", "--name-only", "-z", baseSha + ".." + current),
                        Duration.ofSeconds(60));
                if (diff.exitCode() == 0) {
                    for (String path : diff.stdout().split("\0")) {
                        if (!path.isBlank()) {
                            files.add(path);
                        }
                    }
                }
            }
        }
        return List.copyOf(files);
    }

    public List<String> violations(List<String> changedFiles, List<String> allowedPaths,
                                   List<String> forbiddenPaths) {
        List<Pattern> allowed = compile(allowedPaths);
        List<Pattern> forbidden = compile(forbiddenPaths);
        List<String> violations = new ArrayList<>();
        for (String file : changedFiles) {
            for (Pattern pattern : forbidden) {
                if (pattern.matcher(file).matches()) {
                    violations.add("forbidden path modified: " + file);
                }
            }
            if (!allowed.isEmpty() && allowed.stream().noneMatch(p -> p.matcher(file).matches())) {
                violations.add("outside allowed scope: " + file);
            }
        }
        return violations;
    }

    /**
     * Commits the worktree exactly as the agent left it. Returns the new HEAD;
     * if the agent already committed, no second commit is created.
     */
    public String commit(Path workspace, String message, String authorName, String authorEmail) {
        String before = head(workspace);
        RawResult add = git(workspace, List.of("add", "-A"), Duration.ofSeconds(60));
        if (add.exitCode() != 0) {
            throw new ValidationException("git add failed: " + add.stderr().strip());
        }
        RawResult commit = git(workspace, List.of(
                "-c", "user.name=" + authorName,
                "-c", "user.email=" + authorEmail,
                "commit", "-m", message), Duration.ofSeconds(120));
        if (commit.exitCode() != 0) {
            String output = (commit.stdout() + "\n" + commit.stderr()).strip();
            if (output.contains("nothing to commit") || output.contains("no changes added")) {
                return head(workspace);
            }
            throw new ValidationException("git commit failed: " + abbreviate(output));
        }
        String after = head(workspace);
        log.info("committed agent change in {}: {}", workspace, after);
        return after == null ? before : after;
    }

    private List<String> statusFiles(Path workspace) {
        RawResult status = git(workspace,
                List.of("status", "--porcelain", "-z", "--untracked-files=all"),
                Duration.ofSeconds(60));
        if (status.exitCode() != 0) {
            throw new ValidationException("git status failed: " + status.stderr().strip());
        }
        List<String> files = new ArrayList<>();
        String[] tokens = status.stdout().split("\0");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < 4) {
                continue;
            }
            String statusCode = token.substring(0, 2);
            files.add(token.substring(3));
            if (statusCode.indexOf('R') >= 0 || statusCode.indexOf('C') >= 0) {
                // Rename/copy entries carry the source path in the next token.
                i++;
            }
        }
        return files;
    }

    private List<Pattern> compile(List<String> globs) {
        if (globs == null) {
            return List.of();
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String glob : globs) {
            if (glob == null || glob.isBlank()) {
                continue;
            }
            patterns.add(Pattern.compile(globToRegex(glob.strip())));
        }
        return patterns;
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                        regex.append("/?");
                        i++;
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.append('$').toString();
    }

    private RawResult git(Path workspace, List<String> args, Duration timeout) {
        List<String> argv = new ArrayList<>();
        argv.add("git");
        argv.add("-C");
        argv.add(workspace.toString());
        argv.addAll(args);
        var builder = new ProcessBuilder(argv).redirectErrorStream(false);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        try {
            Process process = builder.start();
            var stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> read(process.getInputStream()));
            var stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> read(process.getErrorStream()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new RawResult(-1, stdoutFuture.join(), "git command timed out");
            }
            return new RawResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (IOException e) {
            return new RawResult(-1, "", "cannot run git: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RawResult(-1, "", "interrupted");
        }
    }

    private String read(java.io.InputStream stream) {
        try (stream) {
            return new String(stream.readNBytes(5 * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 800 ? value : value.substring(0, 800) + "...";
    }

    private record RawResult(int exitCode, String stdout, String stderr) {
    }
}