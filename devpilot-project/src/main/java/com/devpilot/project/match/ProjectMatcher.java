package com.devpilot.project.match;

import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectCatalog;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.api.ProjectMatchPort;
import com.devpilot.common.api.ProjectMatchResultView;
import com.devpilot.common.api.ProjectMatchResultView.MatchDecision;
import com.devpilot.common.api.ProjectMatchView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 确定性项目匹配器。
 *
 * <p>评分按证据类别汇总，每类只取最强的一条，避免关键词重复抬高分数：
 * <ul>
 *   <li>身份证据（项目 ID / 名称 / 别名 / 服务名）：0.40</li>
 *   <li>日志包名：完整包名 0.45，前两段前缀 0.35</li>
 *   <li>代码路径关键字：0.20</li>
 *   <li>疑似模块与项目元数据关联：0.15</li>
 * </ul>
 * 总分不超过 1.0。阈值：&gt;=0.80 自动匹配，0.60~0.80 人工确认，&lt;0.60 不自动匹配。
 * 用户明确指定且项目已注册时直接返回 1.0；指定项目未注册时不猜测其他项目。
 */
@Component
public class ProjectMatcher implements ProjectMatchPort {

    static final double AUTO_THRESHOLD = 0.80;
    static final double CONFIRM_THRESHOLD = 0.60;
    private static final int MAX_CANDIDATES = 5;

    private final ProjectCatalog catalog;

    public ProjectMatcher(ProjectCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public ProjectMatchResultView match(BugAnalysisSummary summary, String specifiedProjectId) {
        var projects = catalog.listActive();
        if (projects.isEmpty()) {
            return new ProjectMatchResultView(null, 0.0, MatchDecision.UNMATCHED,
                    List.of("项目注册表为空"), List.of());
        }

        if (specifiedProjectId != null && !specifiedProjectId.isBlank()) {
            var specified = projects.stream()
                    .filter(p -> p.id().equalsIgnoreCase(specifiedProjectId.trim()))
                    .findFirst();
            if (specified.isPresent()) {
                var project = specified.get();
                return new ProjectMatchResultView(project.id(), 1.0, MatchDecision.AUTO_MATCHED,
                        List.of("用户明确指定项目 " + project.id()),
                        List.of(new ProjectMatchView(project.id(), project.name(), 1.0, List.of("用户指定"))));
            }
            return new ProjectMatchResultView(null, 0.0, MatchDecision.UNMATCHED,
                    List.of("用户指定的项目未注册: " + specifiedProjectId), List.of());
        }

        var text = buildSearchText(summary);
        if (text.isBlank()) {
            return new ProjectMatchResultView(null, 0.0, MatchDecision.UNMATCHED,
                    List.of("BUG 信息不足，无法匹配项目"), List.of());
        }

        var matches = new ArrayList<ProjectMatchView>();
        for (ProjectDescriptor project : projects) {
            var scored = score(project, summary, text);
            if (!scored.evidence().isEmpty()) {
                matches.add(new ProjectMatchView(project.id(), project.name(), scored.score(), scored.evidence()));
            }
        }
        matches.sort(Comparator.comparingDouble(ProjectMatchView::score).reversed());

        if (matches.isEmpty()) {
            return new ProjectMatchResultView(null, 0.0, MatchDecision.UNMATCHED,
                    List.of("未发现与 BUG 信息相符的项目"), List.of());
        }

        var top = matches.get(0);
        var candidates = matches.stream().limit(MAX_CANDIDATES).toList();
        if (top.score() >= AUTO_THRESHOLD) {
            return new ProjectMatchResultView(top.projectId(), top.score(), MatchDecision.AUTO_MATCHED,
                    top.evidence(), candidates);
        }
        if (top.score() >= CONFIRM_THRESHOLD) {
            return new ProjectMatchResultView(top.projectId(), top.score(), MatchDecision.NEEDS_CONFIRMATION,
                    top.evidence(), candidates);
        }
        return new ProjectMatchResultView(null, top.score(), MatchDecision.UNMATCHED,
                List.of("匹配置信度低于阈值，转人工分派"), candidates);
    }

    private ScoredProject score(ProjectDescriptor project, BugAnalysisSummary summary, String text) {
        var evidence = new ArrayList<String>();
        double total = 0.0;

        var identity = matchIdentity(project, text);
        if (identity != null) {
            total += 0.40;
            evidence.add(identity);
        }

        // errorLogSummary 通常已被摘要，丢失完整堆栈；suspectedModules 保留了从堆栈
        // 提取出的包名/路径，因此两者都参与日志上下文匹配。
        var logContext = joinNonNull(summary.errorLogSummary(), summary.actualResult(),
                summary.description(), String.join(" ", nullSafe(summary.suspectedModules())));
        var logHit = matchLogPackage(logContext, project.logPackages());
        if (logHit != null) {
            total += logHit.score();
            evidence.add(logHit.description());
        }

        var codeHit = firstContained(text, project.codePathKeywords());
        if (codeHit != null) {
            total += 0.20;
            evidence.add("命中代码路径关键字: " + codeHit);
        }

        var moduleHit = matchSuspectedModule(summary.suspectedModules(), project);
        if (moduleHit != null) {
            total += 0.15;
            evidence.add("疑似模块与项目关联: " + moduleHit);
        }

        double score = Math.min(1.0, Math.round(total * 100.0) / 100.0);
        return new ScoredProject(score, evidence);
    }

    /**
     * 身份证据只取一条：ID 优先，其次名称、别名、服务名。
     */
    private static String matchIdentity(ProjectDescriptor project, String text) {
        if (containsToken(text, project.id())) {
            return "命中项目 ID: " + project.id();
        }
        if (containsToken(text, project.name())) {
            return "命中项目名称: " + project.name();
        }
        var aliasHit = firstContained(text, project.aliases());
        if (aliasHit != null) {
            return "命中项目别名: " + aliasHit;
        }
        var serviceHit = firstContained(text, project.serviceNames());
        if (serviceHit != null) {
            return "命中服务名: " + serviceHit;
        }
        return null;
    }

    private static LogHit matchLogPackage(String logContext, List<String> packages) {
        if (logContext == null || logContext.isBlank() || packages == null) {
            return null;
        }
        var haystack = normalize(logContext);
        for (String pkg : packages) {
            if (pkg == null || pkg.isBlank()) {
                continue;
            }
            var normalized = normalize(pkg);
            if (haystack.contains(normalized)) {
                return new LogHit(0.45, "日志包名完整匹配: " + pkg);
            }
            var segments = normalized.split("\\.");
            if (segments.length >= 3) {
                var prefix = segments[0] + "." + segments[1];
                if (haystack.contains(prefix)) {
                    return new LogHit(0.35, "日志包名前缀匹配: " + pkg);
                }
            }
        }
        return null;
    }

    private static String matchSuspectedModule(List<String> modules, ProjectDescriptor project) {
        if (modules == null) {
            return null;
        }
        for (String module : modules) {
            if (module == null || module.isBlank()) {
                continue;
            }
            var normalized = normalize(module);
            boolean related = project.aliases().stream().map(ProjectMatcher::normalize)
                    .anyMatch(a -> a.contains(normalized) || normalized.contains(a))
                    || project.serviceNames().stream().map(ProjectMatcher::normalize)
                    .anyMatch(a -> a.contains(normalized) || normalized.contains(a))
                    || project.logPackages().stream().map(ProjectMatcher::normalize)
                    .anyMatch(a -> a.contains(normalized))
                    || normalize(project.id()).contains(normalized);
            if (related) {
                return module;
            }
        }
        return null;
    }

    private static List<String> nullSafe(List<String> value) {
        return value == null ? List.of() : value;
    }

    private static String firstContained(String text, List<String> candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (containsToken(text, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 纯 ASCII 关键字要求词边界；含中文的关键字直接做包含匹配。
     */
    static boolean containsToken(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        var h = normalize(haystack);
        var n = normalize(needle);
        if (n.isBlank()) {
            return false;
        }
        if (!isAsciiWord(n)) {
            return h.contains(n);
        }
        int index = h.indexOf(n);
        while (index >= 0) {
            boolean leftOk = index == 0 || !isWordChar(h.charAt(index - 1));
            int end = index + n.length();
            boolean rightOk = end >= h.length() || !isWordChar(h.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            index = h.indexOf(n, index + 1);
        }
        return false;
    }

    private static boolean isAsciiWord(String value) {
        return value.chars().allMatch(c -> c < 128);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String buildSearchText(BugAnalysisSummary summary) {
        if (summary == null) {
            return "";
        }
        var parts = new ArrayList<String>();
        add(parts, summary.bugId());
        add(parts, summary.title());
        add(parts, summary.description());
        add(parts, summary.expectedResult());
        add(parts, summary.actualResult());
        add(parts, summary.errorLogSummary());
        add(parts, summary.problemType());
        if (summary.impactScope() != null) {
            summary.impactScope().forEach(v -> add(parts, v));
        }
        if (summary.suspectedModules() != null) {
            summary.suspectedModules().forEach(v -> add(parts, v));
        }
        return normalize(String.join(" ", parts));
    }

    private static String joinNonNull(String... values) {
        var parts = new ArrayList<String>();
        for (String value : values) {
            add(parts, value);
        }
        return String.join(" ", parts);
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('_', '-').trim();
    }

    private record ScoredProject(double score, List<String> evidence) {
    }

    private record LogHit(double score, String description) {
    }
}
