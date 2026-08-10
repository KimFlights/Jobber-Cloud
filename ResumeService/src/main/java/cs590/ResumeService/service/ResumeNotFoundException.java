package cs590.ResumeService.service;

/** Thrown when no resume exists for a given Cognito {@code sub}. Mapped to HTTP 404. */
public class ResumeNotFoundException extends RuntimeException {
    public ResumeNotFoundException(String cognitoSub) {
        super("No resume found for user: " + cognitoSub);
    }
}
