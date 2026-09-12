package com.mocklab.service;

import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProcessingJobService#advance(String)}.
 *
 * @spec: 001-mock-lab-application
 */
class ProcessingJobServiceTest {

    private static final String SAMPLE_ID = "SMP-1001";
    private static final String JOB_ID = "JOB-1001";

    private ProcessingJobService processingJobService;
    private ProcessingJobRepository processingJobRepository;
    private SampleRepository sampleRepository;

    @BeforeEach
    void setUp() {
        processingJobRepository = new ProcessingJobRepository();
        sampleRepository = new SampleRepository();

        processingJobService = new ProcessingJobService(processingJobRepository, sampleRepository);

        Instant now = Instant.now();
        sampleRepository.save(new Sample(
                SAMPLE_ID, "PAT-1001", "ORD-1001", SampleType.BLOOD, SampleStatus.ACCESSIONED, now));
        processingJobRepository.save(new ProcessingJob(
                JOB_ID,
                SAMPLE_ID,
                SampleStatus.ACCESSIONED,
                List.of(new ProcessingJob.StatusChange(SampleStatus.ACCESSIONED, now))));
    }

    @Test
    void advancingFromAccessionedLandsOnReadyForProcessing() {
        Optional<ProcessingJob> result = processingJobService.advance(SAMPLE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().currentStatus()).isEqualTo(SampleStatus.READY_FOR_PROCESSING);
    }

    @Test
    void repeatedAdvancesWalkThroughAllStagesInOrder() {
        SampleStatus[] expectedOrder = SampleStatus.values();

        // starts at ACCESSIONED (index 0); each advance should land on the next one
        for (int i = 1; i < expectedOrder.length; i++) {
            Optional<ProcessingJob> result = processingJobService.advance(SAMPLE_ID);
            assertThat(result).isPresent();
            assertThat(result.get().currentStatus()).isEqualTo(expectedOrder[i]);
        }

        assertThat(processingJobRepository.findBySampleId(SAMPLE_ID).get().currentStatus())
                .isEqualTo(SampleStatus.REPORT_GENERATED);
    }

    @Test
    void advancingPastReportGeneratedIsANoOp() {
        // drive to the final stage
        for (int i = 1; i < SampleStatus.values().length; i++) {
            processingJobService.advance(SAMPLE_ID);
        }
        assertThat(processingJobRepository.findBySampleId(SAMPLE_ID).get().currentStatus())
                .isEqualTo(SampleStatus.REPORT_GENERATED);

        Optional<ProcessingJob> result = processingJobService.advance(SAMPLE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().currentStatus()).isEqualTo(SampleStatus.REPORT_GENERATED);
    }

    @Test
    void eachAdvanceAppendsExactlyOneHistoryEntry() {
        assertThat(processingJobRepository.findById(JOB_ID).get().history()).hasSize(1);

        processingJobService.advance(SAMPLE_ID);
        assertThat(processingJobRepository.findById(JOB_ID).get().history()).hasSize(2);

        processingJobService.advance(SAMPLE_ID);
        assertThat(processingJobRepository.findById(JOB_ID).get().history()).hasSize(3);

        ProcessingJob job = processingJobRepository.findById(JOB_ID).get();
        assertThat(job.history().get(0).status()).isEqualTo(SampleStatus.ACCESSIONED);
        assertThat(job.history().get(1).status()).isEqualTo(SampleStatus.READY_FOR_PROCESSING);
        assertThat(job.history().get(2).status()).isEqualTo(SampleStatus.PROCESSING);
    }

    @Test
    void sampleStatusIsKeptInSyncAfterAdvance() {
        processingJobService.advance(SAMPLE_ID);

        ProcessingJob job = processingJobRepository.findBySampleId(SAMPLE_ID).get();
        Sample sample = sampleRepository.findById(SAMPLE_ID).get();

        assertThat(sample.status()).isEqualTo(job.currentStatus());
        assertThat(sample.status()).isEqualTo(SampleStatus.READY_FOR_PROCESSING);
    }

    @Test
    void advancingUnknownSampleIdReturnsEmpty() {
        Optional<ProcessingJob> result = processingJobService.advance("SMP-NOPE");

        assertThat(result).isEmpty();
    }
}
