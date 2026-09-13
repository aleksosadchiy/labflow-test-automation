package com.mocklab.repository;

import com.mocklab.domain.Report;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of {@link Report} records, keyed by report ID.
 * Also supports lookup by sample ID since the relationship is 1—1.
 * Nothing survives a JVM restart.
 */
@Repository
public class ReportRepository {

    private final ConcurrentHashMap<String, Report> reportsById = new ConcurrentHashMap<>();

    public Optional<Report> findById(String id) {
        return Optional.ofNullable(reportsById.get(id));
    }

    public Optional<Report> findBySampleId(String sampleId) {
        return reportsById.values().stream()
                .filter(report -> report.sampleId().equals(sampleId))
                .findFirst();
    }

    public Report save(Report report) {
        reportsById.put(report.id(), report);
        return report;
    }
}
