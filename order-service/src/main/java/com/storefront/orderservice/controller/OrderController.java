package com.storefront.orderservice.controller;

import com.storefront.orderservice.dto.CreateOrderRequest;
import com.storefront.orderservice.dto.OrderResponse;
import com.storefront.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<UUID> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        UUID orderId = orderService.createOrder(request);
        // Return 202 Accepted with location header (simplified to ID in body)
        return ResponseEntity.accepted().body(orderId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@RequestParam UUID userId) {
        // In a real app, userId would come from auth principal; here we accept as param for simplicity
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
