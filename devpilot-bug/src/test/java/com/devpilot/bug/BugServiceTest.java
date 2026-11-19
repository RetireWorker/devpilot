package com.devpilot.bug;

import com.devpilot.bug.api.CreateBugRequest;
import com.devpilot.bug.domain.BugAnalysisRepository;
import com.devpilot.bug.domain.BugMatchRepository;
import com.devpilot.bug.domain.BugRepository;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectMatchResultView;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestBugConfig.class)
@ActiveProfiles("test")
class BugServiceTest {

    @Autowired
    private BugRepository bugRepository;

    @Autowired
    private BugAnalysisRepository analysisRepository;

    @Autowired
    private BugMatchRepository matchRepository;

    private BugService service;

    @BeforeEach
    void setUp() {
        service = new BugService(bugRepository, analysisRepository, matchRepository,
                input -> new BugAnalysisSummary(input.bugId(), input.title(), input.description(),
                        input.expectedResult(), input.actualResult(), "DATA_DUPLICATION",
                        input.severity(), List.of("同步"), List.of("com.example.sync"),
                        "唯一键冲突", List.of(), "HIGH"),
                (summary, specified) -> new ProjectMatchResultView("demo", 1.0,
                        ProjectMatchResultView.MatchDecision.AUTO_MATCHED,
                        List.of("用户指定"), List.of()));
    }

    @Test
    void createAnalyzeAndMatchPersistsEvidence() {
        var bug = service.create(request("企业信息重复"));

        var analysis = service.analyze(bug.id(), "tester");
        var match = service.matchProject(bug.id(), "tester");

        assertThat(analysis.problemType()).isEqualTo("DATA_DUPLICATION");
        assertThat(match.projectId()).isEqualTo("demo");
        assertThat(service.get(bug.id()).status()).isEqualTo("MATCHED");
        assertThat(service.get(bug.id()).analysis()).isNotNull();
        assertThat(service.get(bug.id()).match()).isNotNull();
    }

    @Test
    void matchingRequiresAnalysisFirst() {
        var bug = service.create(request("未分析"));

        assertThatThrownBy(() -> service.matchProject(bug.id(), "tester"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("先执行 BUG 分析");
    }

    @Test
    void duplicateTitleWithin24HoursIsRejected() {
        service.create(request("重复标题"));

        assertThatThrownBy(() -> service.create(request("重复标题")))
                .isInstanceOf(ConflictException.class);
    }

    private CreateBugRequest request(String title) {
        return new CreateBugRequest(title, "描述", "步骤", "期望", "实际",
                "prod", "1.0", "today", "unique constraint", "HIGH", true, "demo");
    }
}
