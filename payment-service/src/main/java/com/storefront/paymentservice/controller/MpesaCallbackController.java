package com.storefront.paymentservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.common.events.EventEnvelope;
import com.storefront.common.events.PaymentCompletedEvent;
import com.storefront.common.events.PaymentFailedEvent;
import com.storefront.paymentservice.domain.Payment;
import com.storefront.paymentservice.domain.PaymentStatus;
import com.storefront.paymentservice.messaging.OutboxEvent;
import com.storefront.paymentservice.messaging.OutboxEventRepository;
import com.storefront.paymentservice.persistence.IdempotencyKeyEntity;
import com.storefront.paymentservice.persistence.IdempotencyKeyRepository;
import com.storefront.paymentservice.repository.PaymentRepository;
import com.storefront.paymentservice.util.HmacUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class MpesaCallbackController {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${payment.mpesa.secret:daraja_hmac_secret_key_2026}")
    private String mpesaSecret;

    @PostMapping(value = "/mpesa/callback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> handleMpesaCallback(
            @RequestHeader(value = "X-Daraja-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        // 1. Verify HMAC signature
        if (signature == null || !HmacUtil.verifyHmac(rawBody, mpesaSecret, signature)) {
            log.warn("Invalid HMAC signature received on Daraja M-Pesa callback");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"Invalid HMAC signature\"}");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode stkCallback = root.path("Body").path("stkCallback");
            if (stkCallback.isMissingNode()) {
                return ResponseEntity.badRequest().body("{\"error\":\"Missing stkCallback object\"}");
            }

            String checkoutRequestId = stkCallback.path("CheckoutRequestID").asText();
            int resultCode = stkCallback.path("ResultCode").asInt();
            String resultDesc = stkCallback.path("ResultDesc").asText();

            // 2. Check Idempotency Key (idempotent by CheckoutRequestID / callback)
            String idempotencyKey = "mpesa_cb_" + checkoutRequestId;
            Optional<IdempotencyKeyEntity> existingKey = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
            if (existingKey.isPresent()) {
                log.info("Duplicate callback received for idempotency key {}. Returning cached response.", idempotencyKey);
                return ResponseEntity.status(existingKey.get().getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(existingKey.get().getResponseBody());
            }

            // Extract OrderId from callback (or match pending payments)
            String orderIdStr = root.path("orderId").asText(null);
            UUID orderId = null;
            if (orderIdStr != null && !orderIdStr.isBlank()) {
                orderId = UUID.fromString(orderIdStr);
            }

            // Extract MpesaReceiptNumber from CallbackMetadata items
            String receiptNumber = "MPESA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            JsonNode items = stkCallback.path("CallbackMetadata").path("Item");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if ("MpesaReceiptNumber".equals(item.path("Name").asText())) {
                        receiptNumber = item.path("Value").asText();
                    }
                }
            }

            // Update Payment Entity if orderId found
            if (orderId != null) {
                Optional<Payment> optPayment = paymentRepository.findByOrderId(orderId);
                if (optPayment.isPresent()) {
                    Payment payment = optPayment.get();
                    if (resultCode == 0) {
                        payment.setStatus(PaymentStatus.COMPLETED);
                        payment.setMpesaReceipt(receiptNumber);
                        paymentRepository.save(payment);

                        // Emit payment.completed to outbox
                        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                                payment.getOrderId(), payment.getId(), payment.getAmount(), "MPESA");
                        String eventJson = objectMapper.writeValueAsString(
                                new EventEnvelope<>(UUID.randomUUID(), payment.getOrderId(), Instant.now(), "payment.completed", payload));
                        outboxEventRepository.save(OutboxEvent.builder()
                                .aggregateId(payment.getOrderId())
                                .topic("payment.completed")
                                .payload(eventJson)
                                .build());
                    } else {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(resultDesc);
                        paymentRepository.save(payment);

                        // Emit payment.failed to outbox
                        PaymentFailedEvent payload = new PaymentFailedEvent(
                                payment.getOrderId(), payment.getId(), resultDesc);
                        String eventJson = objectMapper.writeValueAsString(
                                new EventEnvelope<>(UUID.randomUUID(), payment.getOrderId(), Instant.now(), "payment.failed", payload));
                        outboxEventRepository.save(OutboxEvent.builder()
                                .aggregateId(payment.getOrderId())
                                .topic("payment.failed")
                                .payload(eventJson)
                                .build());
                    }
                }
            }

            String responseJson = String.format("{\"ResultCode\":0,\"ResultDesc\":\"Callback processed successfully\",\"checkoutRequestId\":\"%s\"}", checkoutRequestId);

            // Save idempotency key record
            IdempotencyKeyEntity record = IdempotencyKeyEntity.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(orderId != null ? orderId : UUID.randomUUID())
                    .requestHash(sha256Hex(rawBody))
                    .responseBody(responseJson)
                    .statusCode(HttpStatus.OK.value())
                    .build();
            idempotencyKeyRepository.save(record);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);

        } catch (Exception e) {
            log.error("Error processing Daraja M-Pesa callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Internal error processing callback: " + e.getMessage() + "\"}");
        }
    }

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
