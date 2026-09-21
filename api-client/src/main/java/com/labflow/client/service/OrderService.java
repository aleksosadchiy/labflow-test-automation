package com.labflow.client.service;

import com.labflow.client.api.OrderApi;
import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Order;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business verbs for orders. Each one expects the lab to accept the request and returns
 * the domain object; if the lab answers anything else it throws
 * {@link com.labflow.client.http.UnexpectedResponseError}, whose message names the request,
 * the status expected and the status (and lab message) received.
 */
public final class OrderService {

    private final OrderApi api;

    public OrderService(OrderApi api) {
        this.api = api;
    }

    /** Places an order for a registered patient; the lab must answer 201. */
    public Order place(String patientId) {
        return api.place(Map.of("patientId", patientId)).bodyOrFail(201);
    }

    /**
     * The "refusal expected" twin of {@link #place}: sends whatever it is given and returns
     * the lab's answer, whatever it is, without ever throwing for a refusal. A {@code null}
     * patient id is left out of the request (the body is {@code {}}).
     */
    public LabResponse<Order> attemptPlace(String patientId) {
        Map<String, String> body = new LinkedHashMap<>();
        if (patientId != null) {
            body.put("patientId", patientId);
        }
        return api.place(body);
    }

    /** Looks an order up by the id the lab assigned; the lab must answer 200. */
    public Order find(String id) {
        return api.find(id).bodyOrFail(200);
    }
}
