package com.labflow.client.api;

import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Order;
import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * The order endpoints of the lab: one method per endpoint, no interpretation. It never
 * asserts and never throws for a "wrong" status; whatever the lab answers comes back as a
 * {@link LabResponse}. Business verbs live one layer up, in
 * {@link com.labflow.client.service.OrderService}.
 */
public final class OrderApi {

    private static final String ORDERS = "/api/orders";

    private final Supplier<RequestSpecification> specs;

    /**
     * @param specs supplies a fresh request specification for every request (a
     *              RestAssured specification is mutable, so it is never reused)
     */
    public OrderApi(Supplier<RequestSpecification> specs) {
        this.specs = specs;
    }

    /**
     * {@code POST /api/orders}.
     *
     * @param body what to send as the JSON body: a record, a {@code Map}, or a raw
     *             {@code String} (sent as-is, for deliberately malformed JSON)
     */
    public LabResponse<Order> place(Object body) {
        return LabResponse.from(specs.get().body(body).post(ORDERS), "POST", ORDERS, Order.class);
    }

    /** {@code GET /api/orders/{id}}. */
    public LabResponse<Order> find(String id) {
        return LabResponse.from(specs.get().get(ORDERS + "/{id}", id), "GET", ORDERS + "/" + id, Order.class);
    }
}
