package com.mocklab.repository;

import com.mocklab.domain.TestResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link TestResult} records, keyed by result ID.
 * Also supports lookup by sample ID since the relationship is 1—1.
 * Nothing survives a JVM restart.
 */
@Repository
public class TestResultRepository {

    private final ConcurrentHashMap<String, TestResult> resultsById = new ConcurrentHashMap<>();

    public Optional<TestResult> findById(String id) {
        return Optional.ofNullable(resultsById.get(id));
    }

    public Optional<TestResult> findBySampleId(String sampleId) {
        return resultsById.values().stream()
                .filter(result -> result.sampleId().equals(sampleId))
                .findFirst();
    }

    public TestResult save(TestResult testResult) {
        resultsById.put(testResult.id(), testResult);
        return testResult;
    }
}
