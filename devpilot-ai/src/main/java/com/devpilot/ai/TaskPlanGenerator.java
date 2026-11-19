package com.devpilot.ai;

import com.devpilot.common.api.AgentRole;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.SubtaskSpec;
import com.devpilot.common.api.TaskPlanView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskPlanGenerator {

    public TaskPlanView generate(BugAnalysisSummary bug, ProjectDescriptor project, double confidence) {
        var allowed = List.of("src/main/java/**", "src/test/java/**");
        var subtasks = List.of(
                new SubtaskSpec("analyze-code", AgentRole.CODE_ANALYZER, "parallel", List.of(),
                        List.of("bug-analysis", "repository-snapshot"),
                        List.of("code-location"), allowed,
                        "定位相关类/方法/调用链并给出证据"),
                new SubtaskSpec("analyze-root-cause", AgentRole.ROOT_CAUSE_ANALYZER, "parallel",
                        List.of(), List.of("bug-analysis", "error-logs"),
                        List.of("root-cause"), allowed,
                        "给出根因假设、证据与验证方法"),
                new SubtaskSpec("analyze-test", AgentRole.TEST_ANALYST, "parallel", List.of(),
                        List.of("bug-analysis", "existing-tests"), List.of("test-plan"),
                        allowed, "提供可复现或回归测试方案"),
                new SubtaskSpec("fix-code", AgentRole.JAVA_DEVELOPER, "serial",
                        List.of("analyze-code", "analyze-root-cause", "analyze-test"),
                        List.of("code-location", "root-cause", "test-plan"),
                        List.of("code-diff", "commit"), allowed,
                        "编译通过、测试通过，不删除或跳过测试"),
                new SubtaskSpec("review-code", AgentRole.CODE_REVIEWER, "serial",
                        List.of("fix-code"), List.of("code-diff"),
                        List.of("review-result"), List.of(),
                        "独立审查通过并记录阻断/建议/风险项"));

        boolean highRisk = "HIGH".equalsIgnoreCase(bug.severity())
                || "CRITICAL".equalsIgnoreCase(bug.severity())
                || confidence < 0.80;
        return new TaskPlanView("", project.id(),
                "修复 " + bug.title(), subtasks, highRisk);
    }
}
