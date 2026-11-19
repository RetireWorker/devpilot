package com.devpilot.ai;

import com.devpilot.common.api.AgentRole;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.TaskPlanView;
import com.devpilot.common.domain.RepositoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPlanGeneratorTest {

    private final TaskPlanGenerator generator = new TaskPlanGenerator();

    @Test
    void producesParallelAnalysisThenSerialDevelopmentAndReview() {
        var plan = generator.generate(bug("MEDIUM"), project(), 0.9);

        assertThat(plan.subtasks()).hasSize(5);
        var fix = plan.subtasks().stream().filter(s -> s.id().equals("fix-code")).findFirst().orElseThrow();
        assertThat(fix.role()).isEqualTo(AgentRole.JAVA_DEVELOPER);
        assertThat(fix.dependsOn()).containsExactlyInAnyOrder(
                "analyze-code", "analyze-root-cause", "analyze-test");
        var review = plan.subtasks().stream().filter(s -> s.id().equals("review-code")).findFirst().orElseThrow();
        assertThat(review.dependsOn()).containsExactly("fix-code");
        assertThat(plan.requiresHumanConfirmation()).isFalse();
    }

    @Test
    void highSeverityOrLowConfidenceRequiresHumanConfirmation() {
        assertThat(generator.generate(bug("HIGH"), project(), 0.95).requiresHumanConfirmation()).isTrue();
        assertThat(generator.generate(bug("LOW"), project(), 0.7).requiresHumanConfirmation()).isTrue();
    }

    private BugAnalysisSummary bug(String severity) {
        return new BugAnalysisSummary("BUG-1", "标题", "描述", "期望", "实际",
                "DATA_DUPLICATION", severity, List.of("同步"), List.of("com.example"),
                "唯一键冲突", List.of(), "HIGH");
    }

    private ProjectDescriptor project() {
        return new ProjectDescriptor("demo", "演示项目", "desc", List.of("demo"),
                List.of("demo-svc"), List.of("com.example"), List.of("service"),
                "team", "owner", "java", "17", "spring-boot",
                RepositoryType.GIT, "ssh://demo.git", "main", null, null, null,
                "git-worktree", "workspaces/demo", "maven-verify", "maven-test",
                false, false, true, true, true);
    }
}
