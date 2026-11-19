package com.devpilot.gateway.orca;

import com.devpilot.common.api.AgentExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Composes the self-contained task spec handed to an Orca worker. The spec is a
 * file read by the agent, never a shell argument, so arbitrary task text cannot
 * reach a command line.
 */
@Component
public class WorkerSpecFactory {

    public String compose(AgentExecutionRequest request) {
        StringBuilder spec = new StringBuilder();
        spec.append("# Task ").append(request.taskId());
        if (request.subtaskId() != null) {
            spec.append(" / ").append(request.subtaskId());
        }
        spec.append("\n\n");
        spec.append("Role: ").append(request.role()).append("\n\n");
        spec.append("## Objective\n\n")
                .append(request.objective() == null || request.objective().isBlank()
                        ? "完成子任务：" + request.subtaskId() : request.objective())
                .append("\n\n");

        spec.append("## Workspace\n\n")
                .append("Work only inside the current working directory (the task worktree). ")
                .append("Never read or modify files outside it.\n\n");

        appendScope(spec, "Allowed to modify", request.allowedPaths());
        appendScope(spec, "Forbidden to modify", request.forbiddenPaths());

        spec.append("## Rules\n\n")
                .append("- Make the smallest change that satisfies the objective; keep unrelated code untouched.\n")
                .append("- Do not run `git commit`, `git push`, `git svn dcommit`, or any deployment command; ")
                .append("the platform records and delivers changes itself.\n")
                .append("- Never delete tests, skip tests, or weaken assertions to make a build pass.\n")
                .append("- Do not read credentials, secrets, or files outside the workspace.\n")
                .append("- If the objective cannot be completed, stop and explain the blocker in the final answer.\n")
                .append("- The platform reads only the first ~1200 characters of your final message, ")
                .append("so the required JSON block must come first.\n\n");

        if (request.role() == com.devpilot.common.api.AgentRole.CODE_REVIEWER) {
            spec.append("## Required final answer\n\n")
                    .append("Start your final message with exactly one fenced ```json block shaped like:\n\n")
                    .append("```json\n")
                    .append("{\"summary\":\"one-line verdict\",\"review\":{\"passed\":true,")
                    .append("\"blocking\":[],\"warnings\":[],\"risks\":[],\"summary\":\"why\"}}\n")
                    .append("```\n\n")
                    .append("Set `passed` to false and fill `blocking` for any correctness, security, ")
                    .append("compatibility, or test-coverage defect. Review the actual diff, not the author's claims. ")
                    .append("Keep the JSON under 500 characters: put one-line items only. ")
                    .append("Then add at most three short lines of detail.\n");
        } else {
            spec.append("## Required final answer\n\n")
                    .append("Start your final message with exactly one fenced ```json block shaped like:\n\n")
                    .append("```json\n")
                    .append("{\"summary\":\"what changed and why\",\"changedFiles\":[\"relative/path\"],")
                    .append("\"testsRun\":\"command or none\",\"notes\":\"optional\"}\n")
                    .append("```\n\nKeep the JSON under 500 characters, then add at most three short lines.\n");
        }
        return spec.toString();
    }

    private void appendScope(StringBuilder spec, String title, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        spec.append("## ").append(title).append("\n\n");
        for (String path : paths) {
            spec.append("- `").append(path).append("`\n");
        }
        spec.append('\n');
    }
}