package com.storefront.orderservice.messaging;

import com.storefront.common.events.EventEnvelope;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OrderServiceMessagingIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_db")
            .withUsername("storefront_user")
            .withPassword("storefront_password");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private DltEventRepository dltEventRepository;

    @Test
    void poisonedMessageShouldEndInDlt() throws Exception {
        // Build a poison payload that will trigger RuntimeException in listener
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("poison", true); // marker for test
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.time.Instant.now(),
                "order.created",
                payload
        );
        // Send to the topic – key can be orderId
        kafkaTemplate.send("order.created", envelope.orderId().toString(), envelope);
        // Allow some time for processing and retries (max backoff ~8s, 3 attempts)
        Thread.sleep(15000L);
        // Verify that a DLT record was persisted
        long dltCount = dltEventRepository.count();
        Assertions.assertTrue(dltCount > 0, "DLT event should have been persisted after retries");
    }
}
