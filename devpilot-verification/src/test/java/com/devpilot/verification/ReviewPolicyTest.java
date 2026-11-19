package com.devpilot.verification;

import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.VerificationResultView;
import com.devpilot.verification.review.ReviewPolicy;
import com.devpilot.verification.review.ReviewerResultParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPolicyTest {

    private CodeChangeView change(List<CodeChangeView.ChangedFile> files) {
        return new CodeChangeView("T1", "W1", "ai/T1", "abc123", "develop", files, 10, 2);
    }

    @Test
    void blocksDeletedTestFile() {
        var result = ReviewPolicy.evaluate(
                change(List.of(new CodeChangeView.ChangedFile("src/test/java/A.java", "DELETED", 0, 50))),
                List.of(), null);

        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).anyMatch(s -> s.contains("删除"));
    }

    @Test
    void blocksDisabledTestAndWeakAssertion() {
        var result = ReviewPolicy.evaluate(
                change(List.of(new CodeChangeView.ChangedFile("src/test/java/A.java", "MODIFIED", 3, 1))),
                List.of(new ReviewPolicy.FilePatch("src/test/java/A.java",
                        "@Disabled(\"flaky\")\n// assertThat(x).isEqualTo(1)")),
                null);

        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).anyMatch(s -> s.contains("跳过测试"));
        assertThat(result.blocking()).anyMatch(s -> s.contains("注释掉断言"));
    }

    @Test
    void blocksSwallowedExceptionAndDisabledSecurity() {
        var result = ReviewPolicy.evaluate(
                change(List.of(new CodeChangeView.ChangedFile("src/main/java/A.java", "MODIFIED", 4, 1))),
                List.of(new ReviewPolicy.FilePatch("src/main/java/A.java",
                        "try { x(); } catch (Exception e) {}\nhttp.csrf().disable();")),
                null);

        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).anyMatch(s -> s.contains("吞异常"));
        assertThat(result.blocking()).anyMatch(s -> s.contains("安全控制"));
    }

    @Test
    void warnsWhenProductionChangedWithoutTests() {
        var result = ReviewPolicy.evaluate(
                change(List.of(new CodeChangeView.ChangedFile("src/main/java/A.java", "MODIFIED", 5, 0))),
                List.of(new ReviewPolicy.FilePatch("src/main/java/A.java", "x()")),
                null);

        assertThat(result.passed()).isTrue();
        assertThat(result.risks()).anyMatch(s -> s.contains("未新增或修改测试"));
        assertThat(result.summary()).contains("policy-only");
    }

    @Test
    void reviewerBlockingOverridesPolicyPass() {
        var reviewer = new VerificationResultView("REVIEW", false, List.of("缺少事务边界"),
                List.of(), List.of(), "reviewer");
        var result = ReviewPolicy.evaluate(
                change(List.of(new CodeChangeView.ChangedFile("src/main/java/A.java", "MODIFIED", 1, 1))),
                List.of(), reviewer);

        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).contains("缺少事务边界");
    }

    @Test
    void malformedReviewerOutputFailsClosed() {
        var parsed = ReviewerResultParser.parse("{not json");
        assertThat(parsed).isNotNull();
        assertThat(parsed.passed()).isFalse();
    }

    @Test
    void missingReviewerContractFailsClosed() {
        // Gateway serializes a missing "review" field as the literal string "null".
        var fromNullLiteral = ReviewerResultParser.parse("null");
        assertThat(fromNullLiteral).isNotNull();
        assertThat(fromNullLiteral.passed()).isFalse();
        assertThat(fromNullLiteral.blocking()).anyMatch(s -> s.contains("结构化结论"));

        var fromEmptyObject = ReviewerResultParser.parse("{}");
        assertThat(fromEmptyObject).isNotNull();
        assertThat(fromEmptyObject.passed()).isFalse();
    }

    @Test
    void reviewerContractIsParsed() {
        var parsed = ReviewerResultParser.parse(
                "{\"passed\":true,\"blocking\":[],\"warnings\":[\"缺少边界测试\"],\"risks\":[],\"summary\":\"ok\"}");

        assertThat(parsed).isNotNull();
        assertThat(parsed.passed()).isTrue();
        assertThat(parsed.warnings()).containsExactly("缺少边界测试");
        assertThat(parsed.summary()).isEqualTo("ok");
    }
}
