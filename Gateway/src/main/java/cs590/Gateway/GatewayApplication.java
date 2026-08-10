package cs590.Gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — the single frontend→backend entry point (ARCHITECTURE.md §3.2). Discovers backend
 * services via Consul and load-balances to them client-side. Cognito JWT validation will attach
 * here later; for now the auth filter is a pass-through that forwards the {@code X-User-Sub} header.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
