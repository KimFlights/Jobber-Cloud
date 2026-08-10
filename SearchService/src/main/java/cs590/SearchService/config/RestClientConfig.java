package cs590.SearchService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient pointed at ResumeService for the match-time embedding fetch.
 *
 * <p>Default path uses Consul discovery + Spring Cloud LoadBalancer: the base URI is
 * {@code lb://ResumeService} and the {@code @LoadBalanced} builder resolves a healthy instance
 * (ARCHITECTURE.md §5, Service discovery). Set {@code resume.client.load-balanced=false} for a
 * direct URL when running a single service without Consul.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "resume.client.load-balanced", havingValue = "true", matchIfMissing = true)
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "resume.client.load-balanced", havingValue = "true", matchIfMissing = true)
    RestClient resumeRestClient(RestClient.Builder loadBalancedRestClientBuilder,
                                @Value("${resume.service.url:lb://ResumeService}") String baseUrl) {
        return loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
    }

    @Bean
    @ConditionalOnProperty(name = "resume.client.load-balanced", havingValue = "false")
    RestClient directResumeRestClient(@Value("${resume.service.url:http://localhost:8081}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
