package com.devpilot.bug.api;

import com.devpilot.bug.BugService;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.api.ProjectMatchResultView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/api/bugs")
public class BugController {

    private final BugService bugService;

    public BugController(BugService bugService) {
        this.bugService = bugService;
    }

    @PostMapping
    public BugView create(@Valid @RequestBody CreateBugRequest request) {
        return bugService.create(request);
    }

    @GetMapping("/{bugId}")
    public BugView get(@PathVariable String bugId) {
        return bugService.get(bugId);
    }

    @GetMapping
    public List<BugView> list(Pageable pageable) {
        return bugService.list(pageable);
    }

    @PostMapping("/{bugId}/analyze")
    public BugAnalysisSummary analyze(@PathVariable String bugId,
                                      @RequestParam(defaultValue = "api") String actor) {
        return bugService.analyze(bugId, actor);
    }

    @PostMapping("/{bugId}/match-project")
    public ProjectMatchResultView match(@PathVariable String bugId,
                                        @RequestParam(defaultValue = "api") String actor) {
        return bugService.matchProject(bugId, actor);
    }
}
