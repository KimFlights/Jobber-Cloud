package cs590.SearchService.service;

import cs590.SearchService.client.ResumeClient;
import cs590.SearchService.client.ResumeEmbedding;
import cs590.SearchService.model.JobDocument;
import cs590.SearchService.repository.JobDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * On-demand, cheap match: pull the resume embedding from ResumeService (REST · circuit breaker),
 * cosine-compare against the job's stored embedding, and persist the resulting percentage as the
 * user's rating. Pure embedding math — no LLM verdict (ARCHITECTURE.md §5, §6.3).
 */
@Service
public class MatchService {

    private final JobDocumentRepository jobRepository;
    private final ResumeClient resumeClient;
    private final UserJobStateService userJobStateService;

    public MatchService(JobDocumentRepository jobRepository,
                        ResumeClient resumeClient,
                        UserJobStateService userJobStateService) {
        this.jobRepository = jobRepository;
        this.resumeClient = resumeClient;
        this.userJobStateService = userJobStateService;
    }

    public int match(String cognitoSub, String jobId) {
        JobDocument job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        List<Float> jobVector = job.getEmbedding();
        if (jobVector == null || jobVector.isEmpty()) {
            throw new MatchUnavailableException("Job " + jobId + " has no embedding");
        }

        Optional<ResumeEmbedding> resume = resumeClient.fetchEmbedding(cognitoSub);
        if (resume.isEmpty() || resume.get().embedding() == null || resume.get().embedding().isEmpty()) {
            throw new MatchUnavailableException("Resume embedding unavailable for " + cognitoSub);
        }

        double cosine = CosineSimilarity.between(jobVector, resume.get().embedding());
        int percent = CosineSimilarity.toPercent(cosine);
        userJobStateService.recordRating(cognitoSub, jobId, percent);
        return percent;
    }
}
