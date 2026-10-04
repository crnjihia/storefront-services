package com.storefront.orderservice.messaging;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventId implements Serializable {
    private UUID eventId;
    private String consumer;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEventId that = (ProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
