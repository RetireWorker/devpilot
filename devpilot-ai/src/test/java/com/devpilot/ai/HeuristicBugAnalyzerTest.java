package com.devpilot.ai;

import com.devpilot.common.api.BugAnalysisInput;
import com.devpilot.common.api.BugAnalysisSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicBugAnalyzerTest {

    private final HeuristicBugAnalyzer analyzer = new HeuristicBugAnalyzer();

    @Test
    void classifiesDuplicateDataFromUniqueConstraint() {
        var summary = analyzer.analyze(input(
                "企业信息同步重复",
                "Duplicate entry for key 'uk_enterprise_code'",
                "com.example.jcxxcj.sync.EnterpriseSyncService",
                "1. 触发定时同步\n2. 查看数据库"));

        assertThat(summary.problemType()).isEqualTo("DATA_DUPLICATION");
        assertThat(summary.impactScope()).contains("数据同步", "定时任务");
        assertThat(summary.suspectedModules()).anyMatch(value -> value.startsWith("com.example.jcxxcj"));
    }

    @Test
    void classifiesNullPointerAndExtractsException() {
        var summary = analyzer.analyze(input("接口报错", "java.lang.NullPointerException at Foo.bar", "", ""));

        assertThat(summary.problemType()).isEqualTo("NULL_POINTER");
        assertThat(summary.errorLogSummary()).isEqualTo("NullPointerException");
    }

    @Test
    void unknownTypeWhenNoKeywordMatches() {
        var summary = analyzer.analyze(input("页面样式问题", "按钮颜色不对", "", ""));

        assertThat(summary.problemType()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingInformationIsReportedWithoutInventing() {
        var summary = analyzer.analyze(new BugAnalysisInput("BUG-1", "标题", "描述", "",
                "期望", "实际", null, null, "", "LOW", false, null));

        assertThat(summary.missingInfo()).contains("错误日志/堆栈", "环境与版本");
        assertThat(summary.reproducibility()).isEqualTo("UNKNOWN");
    }

    private BugAnalysisInput input(String title, String description, String errorLog,
                                   String reproduction) {
        return new BugAnalysisInput("BUG-1", title, description, reproduction,
                "期望", "实际", "prod", "1.0", errorLog, "HIGH", true, null);
    }
}
