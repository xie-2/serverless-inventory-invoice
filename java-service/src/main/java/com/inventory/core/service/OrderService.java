package com.inventory.core.service;

import com.inventory.core.dto.CreateOrderRequest;
import com.inventory.core.dto.OrderResponse;
import com.inventory.core.entity.Customer;
import com.inventory.core.entity.Order;
import com.inventory.core.entity.OrderItem;
import com.inventory.core.entity.Product;
import com.inventory.core.repository.CustomerRepository;
import com.inventory.core.repository.OrderRepository;
import com.inventory.core.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private static final BigDecimal TAX_RATE = new BigDecimal("0.10"); // 10% tax rate
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger(1);

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${orchestrator.callback.url}")
    private String orchestratorCallbackUrl;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customerId: {}", request.getCustomerId());

        // Validate customer exists
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Validate products and inventory with row-level locking
        List<Product> products = new ArrayList<>();
        List<CreateOrderRequest.OrderItemRequest> itemRequests = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdWithLock(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product not found: " + itemRequest.getProductId()));

            if (product.getInventoryQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        String.format("Insufficient inventory for product %s. Available: %d, Requested: %d",
                                product.getName(), product.getInventoryQuantity(), itemRequest.getQuantity()));
            }

            products.add(product);
            itemRequests.add(itemRequest);
        }

        // Create order entity
        String orderId = generateOrderId();
        Order order = Order.builder()
                .orderId(orderId)
                .customer(customer)
                .status("COMPLETED")
                .build();

        // Calculate totals
        long subtotalCents = 0L;
        List<OrderItem> orderItems = new ArrayList<>();

        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            CreateOrderRequest.OrderItemRequest itemRequest = itemRequests.get(i);

            // Update inventory (within transaction)
            int newQuantity = product.getInventoryQuantity() - itemRequest.getQuantity();
            product.setInventoryQuantity(newQuantity);

            // Create order item
            long unitPriceCents = product.getPriceCents();
            long totalPriceCents = unitPriceCents * itemRequest.getQuantity();
            subtotalCents += totalPriceCents;

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPriceCents(unitPriceCents)
                    .totalPriceCents(totalPriceCents)
                    .build();

            orderItems.add(orderItem);
        }

        // Calculate tax and total
        BigDecimal subtotal = centsToBigDecimal(subtotalCents);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        order.setSubtotalCents(subtotalCents);
        order.setTaxCents(bigDecimalToCents(tax));
        order.setTotalCents(bigDecimalToCents(total));
        order.setItems(orderItems);

        // Save order (transaction commits here)
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully: {}", savedOrder.getOrderId());

        // Emit callback AFTER transaction commit
        // Using TransactionSynchronizationManager to ensure callback happens after commit
        final String orderIdToNotify = savedOrder.getOrderId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                notifyOrderCompleted(orderIdToNotify);
            }
        });

        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderId(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        // Force loading of lazy relationships
        order.getItems().size(); // Trigger lazy loading
        order.getCustomer().getName(); // Trigger lazy loading
        return mapToResponse(order);
    }

    private void notifyOrderCompleted(String orderId) {
        // Fire-and-forget callback to Node.js orchestrator
        // This happens after transaction commit
        try {
            WebClient webClient = webClientBuilder.build();
            webClient.post()
                    .uri(orchestratorCallbackUrl)
                    .bodyValue(new OrderCompletedCallback(orderId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            result -> log.info("Order completion callback sent successfully for orderId: {}", orderId),
                            error -> log.error("Failed to send order completion callback for orderId: {}", orderId, error)
                    );
        } catch (Exception e) {
            log.error("Error sending order completion callback for orderId: {}", orderId, e);
            // Don't throw - callback failure shouldn't fail the order
        }
    }

    private String generateOrderId() {
        String year = String.valueOf(Year.now().getValue());
        int sequence = ORDER_COUNTER.getAndIncrement();
        return String.format("ORD-%s-%06d", year, sequence);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(centsToBigDecimal(item.getUnitPriceCents()))
                        .totalPrice(centsToBigDecimal(item.getTotalPriceCents()))
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderId(order.getOrderId())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getName())
                .customerEmail(order.getCustomer().getEmail())
                .items(itemResponses)
                .subtotal(centsToBigDecimal(order.getSubtotalCents()))
                .tax(centsToBigDecimal(order.getTaxCents()))
                .total(centsToBigDecimal(order.getTotalCents()))
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private long bigDecimalToCents(BigDecimal amount) {
        return amount.multiply(new BigDecimal("100")).longValue();
    }

    // DTO for callback
    private record OrderCompletedCallback(String orderId) {}
}
