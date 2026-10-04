package com.storefront.orderservice.messaging;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DltListener {

    private final DltEventRepository dltEventRepository;

    @KafkaListener(topics = "order.created.DLT", groupId = "order-service-dlt")
    public void handleDlt(ConsumerRecord<String, Object> record) {
        String payload = record.value() != null ? record.value().toString() : null;
        // Attempt to extract error message from headers (spring.exceptionMessage) if present
        String error = null;
        if (record.headers() != null) {
            var header = record.headers().lastHeader("spring.exceptionMessage");
            if (header != null) {
                error = new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        DltEvent dlt = DltEvent.builder()
                .topic(record.topic())
                .payload(payload)
                .error(error)
                .build();
        dltEventRepository.save(dlt);
    }
}
