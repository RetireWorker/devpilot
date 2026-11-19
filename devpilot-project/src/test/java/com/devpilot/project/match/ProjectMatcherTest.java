package com.devpilot.project.match;

import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.ProjectMatchResultView.MatchDecision;
import com.devpilot.common.domain.RepositoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMatcherTest {

    private final List<ProjectDescriptor> projects = List.of(
            project("tyjr-jcxxcj-service", "企业信息采集服务",
                    List.of("jcxxcj", "企业采集"),
                    List.of("jcxxcj-service"),
                    List.of("com.example.jcxxcj"),
                    List.of("src/main/java/com/example/jcxxcj")),
            project("tyjr-handle", "业务处理服务",
                    List.of("handle", "办理"),
                    List.of("tyjr-handle"),
                    List.of("com.example.handle"),
                    List.of("src/main/java/com/example/handle"))
    );

    private final ProjectMatcher matcher = new ProjectMatcher(new com.devpilot.common.api.ProjectCatalog() {
        @Override
        public java.util.Optional<ProjectDescriptor> findById(String projectId) {
            return projects.stream().filter(p -> p.id().equals(projectId)).findFirst();
        }

        @Override
        public List<ProjectDescriptor> listActive() {
            return projects;
        }
    });

    @Test
    void aliasMatchProducesAutoDecision() {
        var summary = bug("企业采集任务重复写入数据", "com.example.jcxxcj.SyncJob 抛出唯一键冲突");

        var result = matcher.match(summary, null);

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_MATCHED);
        assertThat(result.projectId()).isEqualTo("tyjr-jcxxcj-service");
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.80);
        assertThat(result.signals()).anyMatch(s -> s.contains("别名") || s.contains("名称"));
    }

    @Test
    void logPackageMatchSelectsProject() {
        var summary = bug("同步失败", "Caused by: java.sql.SQLIntegrityConstraintViolationException "
                + "at com.example.jcxxcj.repository.EnterpriseRepository.save(EnterpriseRepository.java:88)");

        var result = matcher.match(summary, null);

        assertThat(result.projectId()).isEqualTo("tyjr-jcxxcj-service");
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.signals()).anyMatch(s -> s.contains("日志包名"));
    }

    @Test
    void noSignalIsUnmatched() {
        var result = matcher.match(bug("页面颜色不对", "按钮偏蓝"), null);

        assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
        assertThat(result.projectId()).isNull();
    }

    @Test
    void blankSummaryIsUnmatched() {
        var summary = new BugAnalysisSummary(null, null, null, null, null,
                null, null, null, null, null, null, null);

        var result = matcher.match(summary, null);

        assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
        assertThat(result.confidence()).isZero();
    }

    @Test
    void specifiedProjectWinsWithoutScoring() {
        var result = matcher.match(bug("企业采集", "com.example.jcxxcj.SyncJob"), "tyjr-handle");

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_MATCHED);
        assertThat(result.projectId()).isEqualTo("tyjr-handle");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void unknownSpecifiedProjectDoesNotGuess() {
        var result = matcher.match(bug("企业采集", "com.example.jcxxcj.SyncJob"), "not-registered");

        assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
        assertThat(result.projectId()).isNull();
        assertThat(result.signals()).anyMatch(s -> s.contains("未注册"));
    }

    @Test
    void wordBoundaryAvoidsSubstringFalsePositive() {
        assertThat(ProjectMatcher.containsToken("the handling service", "handle")).isFalse();
        assertThat(ProjectMatcher.containsToken("tyjr-handle 服务报错", "tyjr-handle")).isTrue();
    }

    @Test
    void confidenceNeverExceedsOne() {
        var summary = bug("tyjr-jcxxcj-service 企业采集 jcxxcj 同步失败",
                "com.example.jcxxcj.SyncJob src/main/java/com/example/jcxxcj");

        var result = matcher.match(summary, null);

        assertThat(result.confidence()).isLessThanOrEqualTo(1.0);
        assertThat(result.candidates()).allSatisfy(c -> assertThat(c.score()).isLessThanOrEqualTo(1.0));
    }

    @Test
    void suspectedModuleRecoversPackageWhenSummaryIsAlreadyDigested() {
        // 真实链路中 errorLogSummary 会被摘要成异常类名，完整包名只剩在 suspectedModules 中。
        var summary = new BugAnalysisSummary("BUG-10002", "同步重复", "同步重复",
                "仅一条", "重复行", "DATA_DUPLICATION", "HIGH",
                List.of("定时任务"),
                List.of("com.example.jcxxcj.sync", "/api/enterprise/sync"),
                "DuplicateKeyException", List.of(), "HIGH");

        var result = matcher.match(summary, null);

        assertThat(result.projectId()).isEqualTo("tyjr-jcxxcj-service");
        assertThat(result.signals()).anyMatch(s -> s.contains("日志包名"));
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.60);
    }

    private static BugAnalysisSummary bug(String description, String actualResult) {
        return new BugAnalysisSummary("BUG-10001", description, description,
                null, actualResult, "DATA_DUPLICATION", "HIGH",
                List.of(), List.of(), actualResult, List.of(), "HIGH");
    }

    private static ProjectDescriptor project(String id, String name, List<String> aliases,
                                             List<String> services, List<String> packages, List<String> paths) {
        return new ProjectDescriptor(id, name, null, aliases, services, packages, paths,
                "team", "owner", "java", "8", "spring-boot",
                RepositoryType.GIT, "ssh://git/example.git", "develop",
                null, null, null, "git-worktree", null,
                "maven-verify", "maven-test",
                false, false, true, true, true);
    }
}
