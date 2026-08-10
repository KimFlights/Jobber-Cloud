package cs590.SearchService.service;

import cs590.SearchService.event.EnrichedJob;
import cs590.SearchService.model.JobDocument;
import cs590.SearchService.repository.JobDocumentRepository;
import org.springframework.stereotype.Service;

/** Maps an {@link EnrichedJob} event onto a {@link JobDocument} and upserts it into Elasticsearch. */
@Service
public class JobIndexer {

    private final JobDocumentRepository repository;

    public JobIndexer(JobDocumentRepository repository) {
        this.repository = repository;
    }

    public void index(EnrichedJob job) {
        JobDocument doc = new JobDocument();
        doc.setJobId(job.jobId());
        doc.setQuery(job.query());
        doc.setSource(job.source());
        doc.setUrl(job.url());
        doc.setTitle(job.title());
        doc.setCompany(job.company());
        doc.setLocation(job.location());
        doc.setSummary(job.summary());
        doc.setCoreRequirements(job.coreRequirements());
        doc.setExperienceLevel(job.experienceLevel());
        doc.setMustHaves(job.mustHaves());
        doc.setNiceToHaves(job.niceToHaves());
        doc.setResponsibilities(job.responsibilities());
        doc.setEmbedding(job.embedding());
        doc.setEnrichedAt(job.enrichedAt());
        repository.save(doc);
    }
}
