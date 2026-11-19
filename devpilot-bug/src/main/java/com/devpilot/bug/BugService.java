package com.devpilot.bug;

import com.devpilot.bug.api.BugView;
import com.devpilot.bug.api.CreateBugRequest;
import com.devpilot.bug.domain.BugAnalysisEntity;
import com.devpilot.bug.domain.BugAnalysisRepository;
import com.devpilot.bug.domain.BugEntity;
import com.devpilot.bug.domain.BugMatchEntity;
import com.devpilot.bug.domain.BugMatchRepository;
import com.devpilot.bug.domain.BugRepository;
import com.devpilot.common.api.AiAnalysisPort;
import com.devpilot.common.api.BugAnalysisInput;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectMatchPort;
import com.devpilot.common.api.ProjectMatchResultView;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.error.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BugService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BugRepository bugRepository;
    private final BugAnalysisRepository analysisRepository;
    private final BugMatchRepository matchRepository;
    private final AiAnalysisPort aiPort;
    private final ProjectMatchPort matchPort;

    public BugService(BugRepository bugRepository,
                      BugAnalysisRepository analysisRepository,
                      BugMatchRepository matchRepository,
                      AiAnalysisPort aiPort,
                      ProjectMatchPort matchPort) {
        this.bugRepository = bugRepository;
        this.analysisRepository = analysisRepository;
        this.matchRepository = matchRepository;
        this.aiPort = aiPort;
        this.matchPort = matchPort;
    }

    @Transactional
    public BugView create(CreateBugRequest request) {
        String title = request.title().trim();
        var recent = bugRepository.findFirstByTitleAndStatusNotAndCreatedAtAfter(
                title, "CLOSED", Instant.now().minus(Duration.ofHours(24)));
        if (recent.isPresent()) {
            throw new ConflictException("24 小时内已存在相同标题的 BUG: " + recent.get().getId());
        }
        String id = "BUG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BugEntity bug = BugEntity.create(id, title, request.description(),
                request.reproductionSteps(), request.expectedResult(), request.actualResult(),
                request.environment(), request.version(), request.timeRange(), request.errorLog(),
                normalizeSeverity(request.severity()), Boolean.TRUE.equals(request.productionIssue()),
                trimToNull(request.specifiedProjectId()));
        bugRepository.save(bug);
        return toView(bug);
    }

    @Transactional(readOnly = true)
    public BugView get(String bugId) {
        return toView(requireBug(bugId));
    }

    @Transactional(readOnly = true)
    public java.util.List<BugView> list(Pageable pageable) {
        return bugRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toView).getContent();
    }

    /**
     * Analysis is idempotent: re-running returns the stored report unless the
     * caller explicitly re-analyzes, which appends a new report version.
     */
    @Transactional
    public BugAnalysisSummary analyze(String bugId, String actor) {
        BugEntity bug = requireBug(bugId);
        var existing = analysisRepository.findFirstByBugIdOrderByCreatedAtDesc(bugId);
        if (existing.isPresent() && !"CREATED".equals(bug.getStatus())) {
            return readSummary(existing.get());
        }
        BugAnalysisSummary summary = aiPort.analyzeBug(new BugAnalysisInput(
                bug.getId(), bug.getTitle(), bug.getDescription(), bug.getReproductionSteps(),
                bug.getExpectedResult(), bug.getActualResult(), bug.getEnvironment(),
                bug.getVersion(), bug.getErrorLog(), bug.getSeverity(),
                bug.isProductionIssue(), bug.getSpecifiedProjectId()));

        var entity = new BugAnalysisEntity();
        entity.setId("BA-" + UUID.randomUUID());
        entity.setBugId(bugId);
        entity.setProblemType(summary.problemType());
        entity.setSeverity(summary.severity());
        entity.setImpactScope(serialize(summary.impactScope()));
        entity.setErrorLogSummary(summary.errorLogSummary());
        entity.setSuspectedModules(serialize(summary.suspectedModules()));
        entity.setReproducibility(summary.reproducibility());
        entity.setMissingInfo(serialize(summary.missingInfo()));
        entity.setRawJson(serialize(summary));
        entity.setCreatedAt(Instant.now());
        analysisRepository.save(entity);
        bug.markStatus("ANALYZED");
        bugRepository.save(bug);
        return summary;
    }

    @Transactional
    public ProjectMatchResultView matchProject(String bugId, String actor) {
        BugEntity bug = requireBug(bugId);
        BugAnalysisSummary summary = analysisRepository.findFirstByBugIdOrderByCreatedAtDesc(bugId)
                .map(this::readSummary)
                .orElseThrow(() -> new ValidationException("请先执行 BUG 分析: " + bugId));

        ProjectMatchResultView result = matchPort.match(summary, bug.getSpecifiedProjectId());
        var entity = new BugMatchEntity();
        entity.setId("BM-" + UUID.randomUUID());
        entity.setBugId(bugId);
        entity.setProjectId(result.projectId());
        entity.setConfidence(result.confidence());
        entity.setSignals(serialize(result.signals()));
        entity.setCandidatesJson(serialize(result.candidates()));
        entity.setDecision(result.decision().name());
        entity.setCreatedAt(Instant.now());
        matchRepository.save(entity);
        bug.markStatus("MATCHED");
        bugRepository.save(bug);
        return result;
    }

    @Transactional(readOnly = true)
    public BugAnalysisSummary requireAnalysis(String bugId) {
        return analysisRepository.findFirstByBugIdOrderByCreatedAtDesc(bugId)
                .map(this::readSummary)
                .orElseThrow(() -> new NotFoundException("BUG 尚无分析结果: " + bugId));
    }

    private BugEntity requireBug(String bugId) {
        return bugRepository.findById(bugId)
                .orElseThrow(() -> new NotFoundException("BUG 不存在: " + bugId));
    }

    private BugView toView(BugEntity bug) {
        var analysis = analysisRepository.findFirstByBugIdOrderByCreatedAtDesc(bug.getId())
                .map(this::readSummary).orElse(null);
        var match = matchRepository.findFirstByBugIdOrderByCreatedAtDesc(bug.getId())
                .map(this::readMatch).orElse(null);
        return new BugView(bug.getId(), bug.getTitle(), bug.getDescription(), bug.getSeverity(),
                bug.isProductionIssue(), bug.getSpecifiedProjectId(), bug.getStatus(),
                bug.getCreatedAt(), analysis, match);
    }

    private BugAnalysisSummary readSummary(BugAnalysisEntity entity) {
        try {
            return MAPPER.readValue(entity.getRawJson(), BugAnalysisSummary.class);
        } catch (Exception e) {
            throw new ValidationException("BUG 分析数据损坏: " + entity.getBugId());
        }
    }

    private ProjectMatchResultView readMatch(BugMatchEntity entity) {
        try {
            var signals = MAPPER.readValue(entity.getSignals(), new TypeReference<List<String>>() { });
            var candidates = MAPPER.readValue(entity.getCandidatesJson(),
                    new TypeReference<List<com.devpilot.common.api.ProjectMatchView>>() { });
            return new ProjectMatchResultView(entity.getProjectId(), entity.getConfidence(),
                    ProjectMatchResultView.MatchDecision.valueOf(entity.getDecision()),
                    signals, candidates);
        } catch (Exception e) {
            throw new ValidationException("BUG 匹配数据损坏: " + entity.getBugId());
        }
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private static String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "MEDIUM";
        }
        String upper = severity.trim().toUpperCase();
        return List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(upper) ? upper : "MEDIUM";
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
