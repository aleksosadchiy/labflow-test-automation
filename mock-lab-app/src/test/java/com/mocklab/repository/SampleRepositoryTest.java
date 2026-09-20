// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.repository;

import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SampleRepository#findByOrderId(String)}. Plain objects,
 * no Spring context.
 */
class SampleRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private SampleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SampleRepository();
    }

    private static Sample sample(String id, String orderId, Instant createdAt) {
        return new Sample(id, "PAT-1", orderId, SampleType.BLOOD, SampleStatus.ACCESSIONED, createdAt);
    }

    @Test
    void findByOrderIdReturnsOnlyThatOrdersSamples() {
        Sample mine = repository.save(sample("SMP-a", "ORD-1", T0));
        repository.save(sample("SMP-b", "ORD-2", T0));

        assertThat(repository.findByOrderId("ORD-1")).containsExactly(mine);
    }

    @Test
    void findByOrderIdReturnsAnEmptyListForAnUnknownOrder() {
        repository.save(sample("SMP-a", "ORD-1", T0));

        assertThat(repository.findByOrderId("ORD-unknown")).isEmpty();
    }

    @Test
    void findByOrderIdOrdersByCreatedAtThenId() {
        // saved in scrambled order on purpose; a HashMap-backed store would not sort them
        Sample lateA = repository.save(sample("SMP-a", "ORD-1", T0.plusSeconds(60)));
        Sample earlyZ = repository.save(sample("SMP-z", "ORD-1", T0));
        Sample earlyB = repository.save(sample("SMP-b", "ORD-1", T0));
        Sample mid = repository.save(sample("SMP-m", "ORD-1", T0.plusSeconds(30)));

        assertThat(repository.findByOrderId("ORD-1")).containsExactly(earlyB, earlyZ, mid, lateA);
    }

    @Test
    void findByOrderIdDoesNotChangeFindAllOrFindById() {
        Sample saved = repository.save(sample("SMP-a", "ORD-1", T0));
        repository.save(sample("SMP-b", "ORD-2", T0));

        repository.findByOrderId("ORD-1");

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findById("SMP-a")).contains(saved);
    }
}
