package com.storefront.paymentservice.listener;

import com.storefront.common.events.EventEnvelope;
import com.storefront.paymentservice.domain.PaymentMethod;
import com.storefront.paymentservice.service.PaymentService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockReservedListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = {"stock.reserved"}, groupId = "payment-service-stock")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        UUID orderId = envelope.orderId();
        // Determine payment method and phone number from the envelope payload if present
        PaymentMethod method = PaymentMethod.CARD; // default
        String phone = null;
        Object payload = envelope.payload();
        if (payload instanceof Map) {
            Map<?,?> map = (Map<?,?>) payload;
            Object methodObj = map.get("paymentMethod");
            if (methodObj != null) {
                try {
                    method = PaymentMethod.valueOf(methodObj.toString().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
            Object phoneObj = map.get("phoneNumber");
            if (phoneObj != null) {
                phone = phoneObj.toString();
            }
        }
        // Amount is not available in this event; use placeholder zero or retrieve from order service if needed
        BigDecimal amount = BigDecimal.ZERO;
        paymentService.initiatePayment(orderId, method, amount, phone);
    }
}
