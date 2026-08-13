package cs590.ScraperService.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Pins the Mongo client + database explicitly. Spring Boot 4.1's Mongo auto-config was observed to
 * ignore {@code spring.data.mongodb.database} (and the URI path db) and fall back to Mongo's
 * default {@code test} database. Providing our own {@link MongoClient} makes Boot back off its
 * auto-configured one, and the factory below forces the configured database name so ScraperService
 * owns a clearly-named {@code scraper} database (ARCHITECTURE.md §2 data ownership).
 */
@Configuration
public class MongoConfig {

    @Bean
    MongoClient mongoClient(
            @Value("${MONGODB_URI:}") String uri,
            @Value("${spring.data.mongodb.host:localhost}") String host,
            @Value("${spring.data.mongodb.port:27017}") int port) {
        // AWS: MONGODB_URI is the full DocumentDB connection string (TLS + retryWrites=false + credentials
        // + authSource). We build the client straight from it — Boot 4.1 won't bind a placeholder onto
        // spring.data.mongodb.uri, so passing it through here is the reliable path.
        if (uri != null && !uri.isBlank()) {
            return MongoClients.create(uri);
        }
        // Local dev: plain host/port Mongo, no auth/TLS.
        return MongoClients.create("mongodb://%s:%d".formatted(host, port));
    }

    @Bean
    MongoDatabaseFactory mongoDatabaseFactory(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database:scraper}") String database) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }
}
