package com.mocklab.api;

import com.mocklab.api.dto.OrderRequest;
import com.mocklab.api.dto.OrderResponse;
import com.mocklab.domain.Order;
import com.mocklab.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * REST surface for orders (functional-spec.md §2.3, technical-considerations.md §2.3).
 *
 * <p>Deliberately thin: every rule lives in {@link OrderService}, and every
 * failure is turned into the standard error body by the {@code /api/**} error
 * resolver. Only the two routes below exist, so any other verb on these paths
 * is a 405. Patient identifiers and request bodies are never logged.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestBody OrderRequest request) {
        Order order = orderService.place(request.patientId());
        URI location = UriComponentsBuilder.fromPath("/api/orders/{id}").build(order.id());
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse find(@PathVariable String id) {
        return OrderResponse.from(orderService.find(id));
    }
}
