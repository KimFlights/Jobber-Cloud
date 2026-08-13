package cs590.JobCompressionService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Surfaces live configuration pulled from the AWS AppConfig Agent sidecar (http://localhost:2772).
 *
 * The sidecar polls AppConfig and caches the latest *deployed* configuration; this controller reads
 * that local cache over HTTP, so a feature flag or freeform value changed in AppConfig (via a
 * deployment/rollout) becomes visible here within the agent's poll interval with NO redeploy of this
 * service. If the sidecar is absent (e.g. local dev, or before it has started) each fetch falls back
 * to an "unavailable" marker so the service still boots and the endpoint never hard-fails.
 */
@RestController
public class AppConfigController {

    private final RestClient client;
    private final String application;
    private final String environment;
    private final String flagsProfile;
    private final String settingsProfile;

    public AppConfigController(
            @Value("${appconfig.agent-url:http://localhost:2772}") String agentUrl,
            @Value("${appconfig.application:jobber}") String application,
            @Value("${appconfig.environment:prod}") String environment,
            @Value("${appconfig.flags-profile:features}") String flagsProfile,
            @Value("${appconfig.settings-profile:settings}") String settingsProfile) {
        this.client = RestClient.create(agentUrl);
        this.application = application;
        this.environment = environment;
        this.flagsProfile = flagsProfile;
        this.settingsProfile = settingsProfile;
    }

    @GetMapping("/appconfig")
    public Map<String, Object> current() {
        return Map.of(
                "flags", fetch(flagsProfile),
                "settings", fetch(settingsProfile));
    }

    private Object fetch(String profile) {
        try {
            return client.get()
                    .uri("/applications/{a}/environments/{e}/configurations/{p}", application, environment, profile)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            return Map.of("unavailable", e.getClass().getSimpleName());
        }
    }
}
