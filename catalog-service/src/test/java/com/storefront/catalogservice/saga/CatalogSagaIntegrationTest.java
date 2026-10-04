package com.storefront.catalogservice.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.catalogservice.domain.Stock;
import com.storefront.catalogservice.domain.StockReservationStatus;
import com.storefront.catalogservice.repository.StockReservationRepository;
import com.storefront.catalogservice.repository.StockRepository;
import com.storefront.common.events.EventEnvelope;
import com.storefront.common.events.StockReservedEvent;
import com.storefront.common.events.StockReservationFailedEvent;
import com.storefront.common.events.StockReleasedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
class CatalogSagaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("catalogdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private com.storefront.catalogservice.messaging.OutboxEventRepository outboxEventRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Clean tables before each test
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void testHappyPathReservationAndConfirmation() throws Exception {
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.builder()
                .productId(productId)
                .available(10)
                .reserved(0)
                .build();
        stockRepository.save(stock);

        UUID orderId = UUID.randomUUID();
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", 2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", List.of(item));

        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), "order.created", payload);
        kafkaTemplate.send("order.created", orderId.toString(), envelope);
        // Allow some time for async processing
        Thread.sleep(5000L);

        Stock updatedStock = stockRepository.findByProductId(productId).orElseThrow();
        Assertions.assertEquals(8, updatedStock.getAvailable(), "Available stock should decrement");
        Assertions.assertEquals(2, updatedStock.getReserved(), "Reserved stock should increment");
        // Verify reservation persisted
        Assertions.assertEquals(1, reservationRepository.findAll().size(), "One reservation should be created");
        // Verify outbox event for stock.reserved
        boolean foundReserved = outboxEventRepository.findAll().stream()
                .anyMatch(e -> e.getTopic().equals("stock.reserved"));
        Assertions.assertTrue(foundReserved, "stock.reserved outbox event should be persisted");

        // Simulate order.confirmed
        EventEnvelope<Void> confirmEnvelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), "order.confirmed", null);
        kafkaTemplate.send("order.confirmed", orderId.toString(), confirmEnvelope);
        Thread.sleep(3000L);

        Stock finalStock = stockRepository.findByProductId(productId).orElseThrow();
        Assertions.assertEquals(8, finalStock.getAvailable(), "Available stock unchanged after confirmation");
        Assertions.assertEquals(0, finalStock.getReserved(), "Reserved stock should be cleared after confirmation");
        // Verify reservation status updated to CONFIRMED
        var reservation = reservationRepository.findAll().get(0);
        Assertions.assertEquals(StockReservationStatus.CONFIRMED, reservation.getStatus(), "Reservation should be confirmed");
    }

    @Test
    void testReservationFailsWhenInsufficientStock() throws Exception {
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.builder()
                .productId(productId)
                .available(5)
                .reserved(0)
                .build();
        stockRepository.save(stock);

        UUID orderId = UUID.randomUUID();
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", 10); // exceeds available
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", List.of(item));

        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), "order.created", payload);
        kafkaTemplate.send("order.created", orderId.toString(), envelope);
        Thread.sleep(5000L);

        Stock unchangedStock = stockRepository.findByProductId(productId).orElseThrow();
        Assertions.assertEquals(5, unchangedStock.getAvailable(), "Stock should remain unchanged on failure");
        Assertions.assertEquals(0, unchangedStock.getReserved(), "No reservation should be made");
        // Verify no reservation records
        Assertions.assertTrue(reservationRepository.findAll().isEmpty(), "No reservation should be persisted");
        // Verify outbox event for stock.reservation-failed
        boolean foundFailed = outboxEventRepository.findAll().stream()
                .anyMatch(e -> e.getTopic().equals("stock.reservation-failed"));
        Assertions.assertTrue(foundFailed, "stock.reservation-failed outbox event should be persisted");
    }

    @Test
    void testCompensationReleaseOnPaymentFailed() throws Exception {
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.builder()
                .productId(productId)
                .available(10)
                .reserved(0)
                .build();
        stockRepository.save(stock);

        UUID orderId = UUID.randomUUID();
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", 3);
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", List.of(item));

        // Create reservation
        EventEnvelope<Map<String, Object>> createEnvelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), "order.created", payload);
        kafkaTemplate.send("order.created", orderId.toString(), createEnvelope);
        Thread.sleep(5000L);

        // Trigger compensation via payment.failed
        EventEnvelope<Void> failEnvelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), "payment.failed", null);
        kafkaTemplate.send("payment.failed", orderId.toString(), failEnvelope);
        Thread.sleep(5000L);

        Stock afterRelease = stockRepository.findByProductId(productId).orElseThrow();
        Assertions.assertEquals(10, afterRelease.getAvailable(), "Available stock restored after release");
        Assertions.assertEquals(0, afterRelease.getReserved(), "Reserved stock cleared after release");
        // Verify reservation status is RELEASED
        var reservation = reservationRepository.findAll().get(0);
        Assertions.assertEquals(StockReservationStatus.RELEASED, reservation.getStatus(), "Reservation should be released");
        // Verify stock.released outbox event
        boolean foundReleased = outboxEventRepository.findAll().stream()
                .anyMatch(e -> e.getTopic().equals("stock.released"));
        Assertions.assertTrue(foundReleased, "stock.released outbox event should be persisted");
    }
}
