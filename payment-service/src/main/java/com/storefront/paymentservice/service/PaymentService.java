package com.storefront.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.common.events.EventEnvelope;
import com.storefront.common.events.PaymentCompletedEvent;
import com.storefront.common.events.PaymentFailedEvent;
import com.storefront.paymentservice.domain.Payment;
import com.storefront.paymentservice.domain.PaymentMethod;
import com.storefront.paymentservice.domain.PaymentStatus;
import com.storefront.paymentservice.gateway.MpesaStkPushGateway;
import com.storefront.paymentservice.gateway.CardGateway;
import com.storefront.paymentservice.gateway.PaymentGateway;
import com.storefront.paymentservice.gateway.PaymentRequest;
import com.storefront.paymentservice.gateway.PaymentResult;
import com.storefront.paymentservice.messaging.IdempotentConsumerHelper;
import com.storefront.paymentservice.messaging.OutboxEvent;
import com.storefront.paymentservice.messaging.OutboxEventRepository;
import com.storefront.paymentservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotentConsumerHelper idempotentHelper;
    private final MpesaStkPushGateway mpesaGateway;
    private final CardGateway cardGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CONSUMER = "payment-service-consumer";

    @Transactional
    public void initiatePayment(UUID orderId, PaymentMethod method, BigDecimal amount, String phone) {
        // Ensure idempotent start: use orderId as eventId placeholder (could be a UUID from stock event)
        UUID eventId = UUID.randomUUID(); // generate a unique id for this initiation
        idempotentHelper.process(CONSUMER, eventId, () -> {
            // Create payment entity with PENDING status
            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .method(method)
                    .amount(amount)
                    .status(PaymentStatus.PENDING)
                    .phone(phone)
                    .createdAt(Instant.now())
                    .build();
            paymentRepository.save(payment);

            // Choose gateway based on method
            PaymentGateway gateway = (method == PaymentMethod.MPESA) ? mpesaGateway : cardGateway;
            PaymentRequest request = new PaymentRequest(orderId, amount, method, phone);
            CompletableFuture<PaymentResult> future = gateway.process(request);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    handleFailure(payment, "Exception: " + ex.getMessage());
                } else if (result.isSuccess()) {
                    handleSuccess(payment, result);
                } else {
                    handleFailure(payment, result.getFailureReason());
                }
            });
        });
    }

    private void handleSuccess(Payment payment, PaymentResult result) {
        payment.setStatus(PaymentStatus.COMPLETED);
        if (payment.getMethod() == PaymentMethod.MPESA) {
            payment.setMpesaReceipt(result.getReceipt());
        }
        paymentRepository.save(payment);
        // Emit payment.completed event
        try {
            PaymentCompletedEvent payload = new PaymentCompletedEvent(payment.getOrderId(), payment.getId(), payment.getAmount(), payment.getMethod().name());
            String json = objectMapper.writeValueAsString(new EventEnvelope<>(UUID.randomUUID(), payment.getOrderId(), Instant.now(), "payment.completed", payload));
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(payment.getOrderId())
                    .topic("payment.completed")
                    .payload(json)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to emit payment.completed outbox event for order {}", payment.getOrderId(), e);
        }
    }

    private void handleFailure(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        try {
            PaymentFailedEvent payload = new PaymentFailedEvent(payment.getOrderId(), payment.getId(), reason);
            String json = objectMapper.writeValueAsString(new EventEnvelope<>(UUID.randomUUID(), payment.getOrderId(), Instant.now(), "payment.failed", payload));
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(payment.getOrderId())
                    .topic("payment.failed")
                    .payload(json)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to emit payment.failed outbox event for order {}", payment.getOrderId(), e);
        }
    }
}
