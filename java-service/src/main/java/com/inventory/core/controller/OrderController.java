package com.inventory.core.controller;

import com.inventory.core.dto.CreateOrderRequest;
import com.inventory.core.dto.OrderResponse;
import com.inventory.core.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received order creation request for customerId: {}", request.getCustomerId());
        try {
            OrderResponse response = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid order request: {}", e.getMessage());
            throw e; // Will be handled by exception handler
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        log.info("Fetching order: {}", orderId);
        try {
            OrderResponse response = orderService.getOrderByOrderId(orderId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Order not found: {}", orderId);
            throw e;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
