package cs590.SearchService.web;

import cs590.SearchService.service.JobSearchService;
import cs590.SearchService.service.MatchService;
import cs590.SearchService.service.UserJobStateService;
import cs590.SearchService.web.dto.JobView;
import cs590.SearchService.web.dto.MatchResponse;
import cs590.SearchService.web.dto.SavedJobView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side + per-user API. The gateway forwards the Cognito {@code sub} as {@code X-User-Sub}.
 * Search returns stale-now instantly; match is on-demand; save/applied mutate per-user state.
 */
@RestController
@RequestMapping("/api/jobs")
public class SearchController {

    static final String USER_HEADER = "X-User-Sub";

    private final JobSearchService jobSearchService;
    private final MatchService matchService;
    private final UserJobStateService userJobStateService;

    public SearchController(JobSearchService jobSearchService,
                           MatchService matchService,
                           UserJobStateService userJobStateService) {
        this.jobSearchService = jobSearchService;
        this.matchService = matchService;
        this.userJobStateService = userJobStateService;
    }

    /** Search the index (STALE-NOW). Triggers a background scrape if the query is thin/stale. */
    @GetMapping
    public List<JobView> search(@RequestParam String query) {
        return jobSearchService.search(query).stream().map(JobView::from).toList();
    }

    /** On-demand cosine match → a single percentage; also stored as the user's rating. */
    @PostMapping("/{jobId}/match")
    public MatchResponse match(@RequestHeader(USER_HEADER) String sub, @PathVariable String jobId) {
        return new MatchResponse(jobId, matchService.match(sub, jobId));
    }

    /** Add a job to the caller's saved list. */
    @PostMapping("/{jobId}/save")
    public SavedJobView save(@RequestHeader(USER_HEADER) String sub, @PathVariable String jobId) {
        return SavedJobView.from(userJobStateService.save(sub, jobId));
    }

    /** Set the caller's applied status for a job. */
    @PutMapping("/{jobId}/applied")
    public SavedJobView applied(@RequestHeader(USER_HEADER) String sub,
                                @PathVariable String jobId,
                                @RequestParam boolean applied) {
        return SavedJobView.from(userJobStateService.setApplied(sub, jobId, applied));
    }

    /** List the caller's saved jobs (with rating + applied status). */
    @GetMapping("/saved")
    public List<SavedJobView> saved(@RequestHeader(USER_HEADER) String sub) {
        return userJobStateService.listSaved(sub).stream().map(SavedJobView::from).toList();
    }
}
