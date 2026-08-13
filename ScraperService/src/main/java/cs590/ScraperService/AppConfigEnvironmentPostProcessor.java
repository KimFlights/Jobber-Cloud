package cs590.ScraperService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Loads this service's ENTIRE configuration from AWS AppConfig at startup, through the AppConfig Agent
 * sidecar (http://localhost:2772). The service's own application.yaml holds only bootstrap identity;
 * everything else lives in the AppConfig '{profile}' document, with secrets written as ${ENV}
 * placeholders resolved against the Parameter-Store-injected environment variables.
 *
 * Runs as an EnvironmentPostProcessor so properties exist before any bean initialises. The AppConfig
 * source is inserted just below systemEnvironment (overrides application.yaml; real env vars still win
 * for ${...} resolution). Retries briefly for the sidecar container; fails fast if config is missing.
 * Active only when APPCONFIG_PROFILE is set (local dev without the sidecar is unaffected).
 */
public class AppConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final int MAX_ATTEMPTS = 15;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String profile = environment.getProperty("APPCONFIG_PROFILE");
        if (profile == null || profile.isBlank()) {
            return;
        }
        String agent = environment.getProperty("APPCONFIG_AGENT_URL", "http://localhost:2772");
        String app = environment.getProperty("APPCONFIG_APPLICATION", "jobber");
        String env = environment.getProperty("APPCONFIG_ENVIRONMENT", "prod");
        String url = "%s/applications/%s/environments/%s/configurations/%s".formatted(agent, app, env, profile);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(5)).build();

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200 && resp.body().length > 0) {
                    List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                            .load("aws-appconfig:" + profile, new ByteArrayResource(resp.body()));
                    for (PropertySource<?> ps : loaded) {
                        environment.getPropertySources()
                                .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, ps);
                    }
                    System.out.println("[AppConfig] Loaded configuration profile '" + profile + "' from " + url);
                    return;
                }
                last = new IllegalStateException("HTTP " + resp.statusCode());
            } catch (Exception e) {
                last = e;
            }
            try {
                Thread.sleep(RETRY_DELAY.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException(
                "Could not load AWS AppConfig profile '" + profile + "' from " + url, last);
    }
}
