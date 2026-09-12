package com.mocklab.repository;

import com.mocklab.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link Order} records, keyed by order ID.
 * Nothing survives a JVM restart.
 */
@Repository
public class OrderRepository {

    private final ConcurrentHashMap<String, Order> ordersById = new ConcurrentHashMap<>();

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    public Order save(Order order) {
        ordersById.put(order.id(), order);
        return order;
    }
}
