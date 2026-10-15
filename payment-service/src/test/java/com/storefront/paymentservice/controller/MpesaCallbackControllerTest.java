package com.storefront.paymentservice.controller;

import com.storefront.paymentservice.persistence.IdempotencyKeyEntity;
import com.storefront.paymentservice.persistence.IdempotencyKeyRepository;
import com.storefront.paymentservice.repository.PaymentRepository;
import com.storefront.paymentservice.messaging.OutboxEventRepository;
import com.storefront.paymentservice.util.HmacUtil;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MpesaCallbackControllerTest {

    private PaymentRepository paymentRepository;
    private OutboxEventRepository outboxEventRepository;
    private IdempotencyKeyRepository idempotencyKeyRepository;
    private MpesaCallbackController controller;

    private static final String SECRET = "test_daraja_secret";

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
        controller = new MpesaCallbackController(paymentRepository, outboxEventRepository, idempotencyKeyRepository);
        ReflectionTestUtils.setField(controller, "mpesaSecret", SECRET);
    }

    @Test
    @DisplayName("Should reject callback with 401 when HMAC signature is invalid")
    void shouldRejectCallbackWhenHmacInvalid() {
        String payload = "{\"Body\":{\"stkCallback\":{\"CheckoutRequestID\":\"ws_123\",\"ResultCode\":0}}}";
        ResponseEntity<String> response = controller.handleMpesaCallback("invalid_signature", payload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return original response on replay of duplicate callback")
    void shouldReturnOriginalResponseOnDuplicateCallback() {
        String checkoutRequestId = "ws_CO_TEST_12345";
        String payload = String.format("{\"Body\":{\"stkCallback\":{\"CheckoutRequestID\":\"%s\",\"ResultCode\":0,\"ResultDesc\":\"Success\"}}}", checkoutRequestId);
        String signature = HmacUtil.calculateHmac(payload, SECRET);

        String cachedResponse = "{\"ResultCode\":0,\"ResultDesc\":\"Callback processed successfully\",\"checkoutRequestId\":\"" + checkoutRequestId + "\"}";
        IdempotencyKeyEntity existingRecord = IdempotencyKeyEntity.builder()
                .idempotencyKey("mpesa_cb_" + checkoutRequestId)
                .orderId(UUID.randomUUID())
                .requestHash("hash123")
                .responseBody(cachedResponse)
                .statusCode(200)
                .build();

        when(idempotencyKeyRepository.findByIdempotencyKey("mpesa_cb_" + checkoutRequestId))
                .thenReturn(Optional.of(existingRecord));

        ResponseEntity<String> response = controller.handleMpesaCallback(signature, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cachedResponse);

        // Verify no duplicate outbox event is saved on replay
        verify(outboxEventRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully process valid callback and persist idempotency key")
    void shouldProcessValidCallbackAndPersistIdempotencyKey() {
        String checkoutRequestId = "ws_CO_NEW_99999";
        String payload = String.format("{\"Body\":{\"stkCallback\":{\"CheckoutRequestID\":\"%s\",\"ResultCode\":0,\"ResultDesc\":\"Success\"}}}", checkoutRequestId);
        String signature = HmacUtil.calculateHmac(payload, SECRET);

        when(idempotencyKeyRepository.findByIdempotencyKey("mpesa_cb_" + checkoutRequestId))
                .thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleMpesaCallback(signature, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Callback processed successfully");

        // Verify idempotency record was saved
        verify(idempotencyKeyRepository).save(argThat(entity ->
                entity.getIdempotencyKey().equals("mpesa_cb_" + checkoutRequestId) &&
                entity.getStatusCode() == 200
        ));
    }
}
