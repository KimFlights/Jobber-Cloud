package cs590.JobCompressionService.config;

import cs590.JobCompressionService.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the topic this service produces. MSK Serverless does not auto-create topics, so Spring's
 * auto-configured KafkaAdmin declares it at startup via an AdminClient (same IAM/SASL props as the
 * clients; the task role already allows kafka-cluster:CreateTopic). Each service owns the topic it
 * publishes to — JobCompressionService owns {@code enriched-job}. Replication factor must be 3 on Serverless.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic enrichedJobTopic() {
        return TopicBuilder.name(Topics.ENRICHED_JOB).partitions(6).replicas(3).build();
    }
}
