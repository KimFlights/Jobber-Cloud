package cs590.SearchService.service;

/** Thrown when a job id is not present in the index. Mapped to HTTP 404. */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("No job found: " + jobId);
    }
}
