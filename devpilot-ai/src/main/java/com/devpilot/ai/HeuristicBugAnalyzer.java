package com.devpilot.ai;

import com.devpilot.common.api.BugAnalysisInput;
import com.devpilot.common.api.BugAnalysisSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic analyzer used when no model is configured. It only extracts
 * facts present in the report: no invented modules or root causes.
 */
@Component
public class HeuristicBugAnalyzer {

    private static final Pattern PACKAGE = Pattern.compile("\\b(com\\.[a-zA-Z0-9_.]+)\\b");
    private static final Pattern API_PATH = Pattern.compile("(/(?:api|rest)/[a-zA-Z0-9_\\-/{}]+)");
    private static final Pattern EXCEPTION = Pattern.compile("\\b([A-Z][A-Za-z0-9]*(?:Exception|Error))\\b");

    public BugAnalysisSummary analyze(BugAnalysisInput input) {
        String text = join(input);
        String lower = text.toLowerCase(Locale.ROOT);

        String problemType = classify(lower);
        List<String> modules = new ArrayList<>(new LinkedHashSet<>(packages(text)));
        List<String> impact = impactScope(lower);
        List<String> missing = missingInfo(input);
        String errorSummary = errorSummary(text);

        return new BugAnalysisSummary(
                input.bugId(), input.title(), input.description(),
                input.expectedResult(), input.actualResult(),
                problemType, input.severity() == null ? "MEDIUM" : input.severity(),
                impact, modules, errorSummary, missing,
                reproducibility(input));
    }

    private String classify(String lower) {
        if (lower.contains("duplicate") || lower.contains("unique constraint")
                || lower.contains("重复") || lower.contains("唯一")) {
            return "DATA_DUPLICATION";
        }
        if (lower.contains("nullpointer") || lower.contains("null pointer")
                || lower.contains("空指针")) {
            return "NULL_POINTER";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
            return "TIMEOUT";
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden")
                || lower.contains("401") || lower.contains("403")) {
            return "AUTH";
        }
        if (lower.contains("deadlock") || lower.contains("lock wait")) {
            return "LOCK_CONTENTION";
        }
        if (lower.contains("outofmemory") || lower.contains("out of memory")) {
            return "RESOURCE_EXHAUSTION";
        }
        return "UNKNOWN";
    }

    private List<String> impactScope(String lower) {
        var scope = new ArrayList<String>();
        if (lower.contains("定时") || lower.contains("schedul") || lower.contains("job")) {
            scope.add("定时任务");
        }
        if (lower.contains("sync") || lower.contains("同步")) {
            scope.add("数据同步");
        }
        if (lower.contains("login") || lower.contains("登录")) {
            scope.add("登录认证");
        }
        if (scope.isEmpty()) {
            scope.add("待确认");
        }
        return List.copyOf(scope);
    }

    private List<String> missingInfo(BugAnalysisInput input) {
        var missing = new ArrayList<String>();
        if (blank(input.errorLog())) {
            missing.add("错误日志/堆栈");
        }
        if (blank(input.reproductionSteps())) {
            missing.add("复现步骤");
        }
        if (blank(input.environment())) {
            missing.add("环境与版本");
        }
        return List.copyOf(missing);
    }

    private String errorSummary(String text) {
        Matcher matcher = EXCEPTION.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return text.isBlank() ? "无错误日志" : firstLine(text, 200);
    }

    private String reproducibility(BugAnalysisInput input) {
        if (blank(input.reproductionSteps()) || blank(input.actualResult())) {
            return "UNKNOWN";
        }
        return "HIGH";
    }

    private List<String> packages(String text) {
        var result = new ArrayList<String>();
        Matcher matcher = PACKAGE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            String[] parts = value.split("\\.");
            if (parts.length >= 4) {
                result.add(String.join(".", java.util.Arrays.copyOf(parts, Math.min(4, parts.length))));
            }
        }
        Matcher pathMatcher = API_PATH.matcher(text);
        while (pathMatcher.find()) {
            result.add(pathMatcher.group(1));
        }
        if (result.isEmpty()) {
            result.add("未从日志中识别到包名或接口路径");
        }
        return List.copyOf(result);
    }

    private String join(BugAnalysisInput input) {
        return String.join("\n", nullToEmpty(input.title()), nullToEmpty(input.description()),
                nullToEmpty(input.reproductionSteps()), nullToEmpty(input.expectedResult()),
                nullToEmpty(input.actualResult()), nullToEmpty(input.errorLog()));
    }

    private static String firstLine(String text, int max) {
        String line = text.lines().findFirst().orElse("");
        return line.length() > max ? line.substring(0, max) : line;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
