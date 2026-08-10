package cs590.Gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Frontend→backend routes. Only the two REST-facing services are exposed (ResumeService,
 * SearchService); ScraperService/JobCompressionService are internal to the event pipeline
 * (ARCHITECTURE.md §3.2). Each route targets the service by name and is load-balanced client-side
 * via {@code lb(...)} over Consul-discovered instances.
 */
@Configuration
public class RouteConfig {

    @Bean
    RouterFunction<ServerResponse> resumeRoute() {
        return route("resume")
                .route(path("/api/resumes/**"), http())
                .filter(lb("ResumeService"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> searchRoute() {
        return route("search")
                .route(path("/api/jobs/**"), http())
                .filter(lb("SearchService"))
                .build();
    }
}
