package com.devpilot.verification.review;

import com.devpilot.common.api.CodeChangeView;
import com.devpilot.common.api.VerificationResultView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic guard against the classic "make the build green" cheats.
 * This runs independently of the reviewer model and its findings are
 * non-negotiable blocking items.
 */
public final class ReviewPolicy {

    private static final Pattern SKIPPED_TEST = Pattern.compile(
            "(?i)@(Disabled|Ignore)\\b|\\.skip\\s*\\(|xit\\s*\\(|xdescribe\\s*\\(");
    private static final Pattern COMMENTED_ASSERT = Pattern.compile(
            "(?m)^\\s*//\\s*(assert|Assert\\.|assertThat|expect\\s*\\()");
    private static final Pattern SWALLOWED_EXCEPTION = Pattern.compile(
            "catch\\s*\\([^)]*(Exception|Throwable)[^)]*\\)\\s*\\{\\s*}");
    private static final Pattern DISABLED_SECURITY = Pattern.compile(
            "(?i)(permitAll\\(\\)|\\.csrf\\(\\)\\s*\\.disable\\(\\)|httpBasic\\(\\)\\s*\\.disable\\(\\))");
    private static final Pattern SUPPRESS_ALL = Pattern.compile(
            "@SuppressWarnings\\(\\s*\"all\"\\s*\\)|@SuppressWarnings\\(\\s*\\{\\s*\"all\"\\s*}\\s*\\)");
    private static final Pattern TEST_PATH = Pattern.compile(
            "(?i)(src/test/|/test/|_test\\.go$|\\.spec\\.(ts|js)$|\\.test\\.(ts|js)$)");

    private ReviewPolicy() {
    }

    public static VerificationResultView evaluate(CodeChangeView change,
                                                  List<FilePatch> patches,
                                                  VerificationResultView reviewer) {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        if (change == null) {
            blocking.add("缺少代码变更记录，无法审查");
        } else {
            if (change.files() == null || change.files().isEmpty()) {
                blocking.add("变更不包含任何文件");
            }
            int totalLines = change.insertions() + change.deletions();
            if (totalLines > 1500) {
                warnings.add("变更规模较大（" + totalLines + " 行），建议拆分审查");
            }
            boolean prodChanged = false;
            boolean testChanged = false;
            for (CodeChangeView.ChangedFile file : change.files() == null
                    ? List.<CodeChangeView.ChangedFile>of() : change.files()) {
                String path = file.path() == null ? "" : file.path();
                boolean isTest = TEST_PATH.matcher(path).find();
                testChanged |= isTest;
                prodChanged |= !isTest;
                if (isTest && "DELETED".equalsIgnoreCase(file.changeType())) {
                    blocking.add("删除了测试文件: " + path);
                }
            }
            if (prodChanged && !testChanged) {
                risks.add("修改了生产代码但未新增或修改测试");
            }
        }

        for (FilePatch patch : patches == null ? List.<FilePatch>of() : patches) {
            String path = patch.path() == null ? "" : patch.path();
            String added = patch.addedText() == null ? "" : patch.addedText();
            boolean isTest = TEST_PATH.matcher(path).find();
            if (SKIPPED_TEST.matcher(added).find()) {
                blocking.add("新增跳过测试标记: " + path);
            }
            if (COMMENTED_ASSERT.matcher(added).find()) {
                blocking.add("注释掉断言: " + path);
            }
            if (SWALLOWED_EXCEPTION.matcher(added).find()) {
                blocking.add("空 catch 吞异常: " + path);
            }
            if (!isTest && DISABLED_SECURITY.matcher(added).find()) {
                blocking.add("疑似关闭安全控制: " + path);
            }
            if (!isTest && SUPPRESS_ALL.matcher(added).find()) {
                blocking.add("生产代码使用 @SuppressWarnings(\"all\"): " + path);
            }
        }

        if (reviewer != null) {
            blocking.addAll(reviewer.blocking());
            warnings.addAll(reviewer.warnings());
            risks.addAll(reviewer.risks());
        }

        boolean passed = blocking.isEmpty();
        String summary = reviewer == null
                ? (passed ? "policy-only: 未发现阻断项" : "policy-only: 发现阻断项")
                : reviewer.summary();
        return new VerificationResultView("REVIEW", passed, List.copyOf(blocking),
                List.copyOf(warnings), List.copyOf(risks), summary);
    }

    public record FilePatch(String path, String addedText) {
    }
}
