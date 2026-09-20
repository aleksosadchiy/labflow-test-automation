package com.mocklab.repository;

import com.mocklab.domain.Sample;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link Sample} records, keyed by sample ID.
 * Nothing survives a JVM restart.
 */
@Repository
public class SampleRepository {

    private final ConcurrentHashMap<String, Sample> samplesById = new ConcurrentHashMap<>();

    public Optional<Sample> findById(String id) {
        return Optional.ofNullable(samplesById.get(id));
    }

    public List<Sample> findAll() {
        return List.copyOf(samplesById.values());
    }

    /**
     * The samples registered under the given order, oldest first (ties broken by
     * ID so the order is deterministic). Empty when the order has none.
     */
    public List<Sample> findByOrderId(String orderId) {
        return samplesById.values().stream()
                .filter(sample -> sample.orderId().equals(orderId))
                .sorted(Comparator.comparing(Sample::createdAt).thenComparing(Sample::id))
                .toList();
    }

    public Sample save(Sample sample) {
        samplesById.put(sample.id(), sample);
        return sample;
    }
}
