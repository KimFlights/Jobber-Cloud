package cs590.ResumeService.dto;

/**
 * Resume upload payload. Kept text-based (rather than a binary PDF) so the pipeline stays the
 * focus; a real deployment would parse an uploaded file to this text first.
 */
public record ResumeUploadRequest(String text) {
}
