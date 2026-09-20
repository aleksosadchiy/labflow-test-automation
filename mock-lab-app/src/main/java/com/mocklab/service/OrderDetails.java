package com.mocklab.service;

import com.mocklab.domain.Order;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;

import java.util.List;

/**
 * An {@link Order} together with a summary of the samples registered under it,
 * as returned by {@link OrderService#find(String)}. Each summary's
 * {@code status} comes from the sample's processing job, the source of truth.
 */
public record OrderDetails(Order order, List<SampleSummary> samples) {

    /** The few sample fields an order read exposes. */
    public record SampleSummary(String id, SampleType sampleType, SampleStatus status) {
    }
}
