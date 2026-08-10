package cs590.SearchService.web.dto;

import cs590.SearchService.model.JobDocument;
import java.time.Instant;
import java.util.List;

/** Client-facing job view. Omits the raw embedding vector (used server-side for match only). */
public record JobView(
        String jobId,
        String source,
        String url,
        String title,
        String company,
        String location,
        String summary,
        List<String> coreRequirements,
        String experienceLevel,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<String> responsibilities,
        Instant enrichedAt) {

    public static JobView from(JobDocument d) {
        return new JobView(
                d.getJobId(), d.getSource(), d.getUrl(), d.getTitle(), d.getCompany(),
                d.getLocation(), d.getSummary(), d.getCoreRequirements(), d.getExperienceLevel(),
                d.getMustHaves(), d.getNiceToHaves(), d.getResponsibilities(), d.getEnrichedAt());
    }
}
