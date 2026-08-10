package cs590.ResumeService.web;

import cs590.ResumeService.dto.EmbeddingResponse;
import cs590.ResumeService.dto.ResumeUploadRequest;
import cs590.ResumeService.dto.ResumeView;
import cs590.ResumeService.model.ResumeEntity;
import cs590.ResumeService.service.ResumeService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for resumes. The gateway validates the Cognito JWT and forwards the user's
 * {@code sub} as {@code X-User-Sub}; services trust that header (ARCHITECTURE.md §5, Auth).
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    static final String USER_HEADER = "X-User-Sub";

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** Upload (or replace) the caller's resume; runs parse → embed → suggested-queries. */
    @PostMapping
    public ResponseEntity<ResumeView> upload(
            @RequestHeader(USER_HEADER) String sub,
            @RequestBody ResumeUploadRequest request) {
        ResumeEntity saved = resumeService.upload(sub, request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResumeView.from(saved));
    }

    /** The caller's own resume view (no embedding). */
    @GetMapping("/me")
    public ResumeView me(@RequestHeader(USER_HEADER) String sub) {
        return ResumeView.from(resumeService.get(sub));
    }

    /**
     * Resume embedding for a given user — consumed by SearchService at match time behind a
     * circuit breaker. Keyed by {@code sub} in the path so a service-to-service call can fetch
     * any user's vector without impersonating a header.
     */
    @GetMapping("/{sub}/embedding")
    public EmbeddingResponse embedding(@org.springframework.web.bind.annotation.PathVariable String sub) {
        ResumeEntity resume = resumeService.get(sub);
        List<Float> vector = resume.getEmbedding();
        return new EmbeddingResponse(sub, vector == null ? 0 : vector.size(), vector);
    }
}
