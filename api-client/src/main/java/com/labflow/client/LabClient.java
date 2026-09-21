package com.labflow.client;

import com.labflow.client.api.OrderApi;
import com.labflow.client.api.PatientApi;
import com.labflow.client.api.SampleApi;
import com.labflow.client.http.LabRequestSpecs;
import com.labflow.client.service.OrderService;
import com.labflow.client.service.PatientService;
import com.labflow.client.service.SampleService;
import com.labflow.common.config.ApiKey;
import com.labflow.common.config.LabConfig;

/**
 * The single entry point to the lab for checks: {@code lab.patients().register(name)}.
 *
 * <p>A client is immutable. {@link #withApiKey(String)} and {@link #withoutApiKey()} never
 * change it, they return a <em>new</em> client, so a check can try a wrong or missing key
 * without affecting any other check running in parallel.
 */
public final class LabClient {

    private final String baseUrl;
    /** Null means "send no Authorization header". */
    private final ApiKey apiKey;
    private final PatientService patients;
    private final OrderService orders;
    private final SampleService samples;

    private LabClient(String baseUrl, ApiKey apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.patients = new PatientService(new PatientApi(() -> LabRequestSpecs.create(baseUrl, apiKey)));
        this.orders = new OrderService(new OrderApi(() -> LabRequestSpecs.create(baseUrl, apiKey)));
        this.samples = new SampleService(new SampleApi(() -> LabRequestSpecs.create(baseUrl, apiKey)));
    }

    /** A client for the lab and access key in the configuration. */
    public static LabClient create(LabConfig config) {
        return new LabClient(config.baseUrl(), config.apiKey());
    }

    public PatientService patients() {
        return patients;
    }

    public OrderService orders() {
        return orders;
    }

    public SampleService samples() {
        return samples;
    }

    /** A new client for the same lab that signs in with the given key instead. */
    public LabClient withApiKey(String key) {
        return new LabClient(baseUrl, new ApiKey(key));
    }

    /** A new client for the same lab that sends no access key at all. */
    public LabClient withoutApiKey() {
        return new LabClient(baseUrl, null);
    }

    /** Never shows the key. */
    @Override
    public String toString() {
        return "LabClient[baseUrl=" + baseUrl + ", apiKey=" + (apiKey == null ? "none" : "***") + "]";
    }
}
