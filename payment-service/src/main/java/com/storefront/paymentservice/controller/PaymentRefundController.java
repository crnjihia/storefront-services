package com.storefront.paymentservice.controller;

import com.storefront.common.events.EventEnvelope;
import com.storefront.paymentservice.domain.Payment;
import com.storefront.paymentservice.events.PaymentRefundedEvent;
import com.storefront.paymentservice.messaging.OutboxEvent;
import com.storefront.paymentservice.messaging.OutboxEventRepository;
import com.storefront.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentRefundController {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<Void> refund(@PathVariable UUID orderId, @RequestBody RefundRequest request) {
        java.util.Optional<Payment> optPayment = paymentRepository.findByOrderId(orderId);
        if (optPayment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Payment payment = optPayment.get();
        if (payment.getStatus() != com.storefront.paymentservice.domain.PaymentStatus.COMPLETED) {
            return ResponseEntity.badRequest().build();
        }
        payment.setStatus(com.storefront.paymentservice.domain.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        try {
            PaymentRefundedEvent payload = new PaymentRefundedEvent(payment.getOrderId(), payment.getId(), payment.getAmount(), request.getReason());
            String json = objectMapper.writeValueAsString(new EventEnvelope<>(UUID.randomUUID(), payment.getOrderId(), Instant.now(), "payment.refunded", payload));
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(payment.getOrderId())
                    .topic("payment.refunded")
                    .payload(json)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            // log error (omitted for brevity)
        }
        return ResponseEntity.ok().build();
    }

    @Data
    public static class RefundRequest {
        private String reason;
    }
}
