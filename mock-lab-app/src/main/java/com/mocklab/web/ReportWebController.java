package com.mocklab.web;

import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Report;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.TestResult;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.repository.TestResultRepository;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * View Report screen (functional-spec.md §2.5). {@code GET
 * /samples/{id}/report} shows the sample's identifier, test name, result
 * value, and report generation date once the sample has reached {@link
 * SampleStatus#REPORT_GENERATED}. Before that stage it shows "Report is not
 * ready yet." (expected, spec'd behavior, not an error), and for an unknown
 * sample ID it reuses the existing "Sample not found." pattern from {@link
 * SampleWebController}.
 */
@Controller
public class ReportWebController {

    private final SampleRepository sampleRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final TestResultRepository testResultRepository;
    private final ReportRepository reportRepository;

    public ReportWebController(
            SampleRepository sampleRepository,
            ProcessingJobRepository processingJobRepository,
            TestResultRepository testResultRepository,
            ReportRepository reportRepository) {
        this.sampleRepository = sampleRepository;
        this.processingJobRepository = processingJobRepository;
        this.testResultRepository = testResultRepository;
        this.reportRepository = reportRepository;
    }

    @GetMapping("/samples/{id}/report")
    public String viewReport(@PathVariable String id, Model model) {
        Optional<Sample> sampleOpt = sampleRepository.findById(id);
        if (sampleOpt.isEmpty()) {
            model.addAttribute("notFound", true);
            return "report";
        }

        Sample sample = sampleOpt.get();
        SampleStatus currentStatus = processingJobRepository.findBySampleId(sample.id())
                .map(ProcessingJob::currentStatus)
                .orElse(sample.status());

        model.addAttribute("notFound", false);

        if (currentStatus != SampleStatus.REPORT_GENERATED) {
            model.addAttribute("notReady", true);
            return "report";
        }

        model.addAttribute("notReady", false);

        TestResult testResult = testResultRepository.findBySampleId(sample.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No TestResult found for sample at REPORT_GENERATED: " + sample.id()));
        Report report = reportRepository.findBySampleId(sample.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No Report found for sample at REPORT_GENERATED: " + sample.id()));

        model.addAttribute("sampleId", sample.id());
        model.addAttribute("testName", testResult.testName());
        model.addAttribute("resultValue", testResult.resultValue());
        model.addAttribute("generatedAt", report.generatedAt().toString());
        return "report";
    }
}
