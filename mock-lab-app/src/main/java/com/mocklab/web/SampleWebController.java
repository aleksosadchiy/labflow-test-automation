package com.mocklab.web;

import com.mocklab.domain.Patient;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.service.ProcessingJobService;
import com.mocklab.service.SampleCreationResult;
import com.mocklab.service.SampleService;
import com.mocklab.web.form.CreateSampleForm;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Sample List, Create Sample, and Sample Details screens. {@code GET
 * /samples} shows every registered sample with its patient name and current
 * status (functional-spec.md §2.2). {@code GET /samples/new} and {@code POST
 * /samples} back the Create Sample form (functional-spec.md §2.3),
 * delegating validation and persistence to {@link SampleService}. {@code GET
 * /samples/{id}} shows a single sample's current status and its full 8-stage
 * progression, or a "Sample not found." message for an unknown ID
 * (functional-spec.md §2.4). {@code POST /samples/{id}/advance} moves the
 * sample to the next stage via {@link ProcessingJobService#advance(String)}
 * and redirects back to the details screen (functional-spec.md §2.4).
 */
@Controller
public class SampleWebController {

    private final SampleService sampleService;
    private final ProcessingJobService processingJobService;
    private final SampleRepository sampleRepository;
    private final PatientRepository patientRepository;
    private final ProcessingJobRepository processingJobRepository;

    public SampleWebController(
            SampleService sampleService,
            ProcessingJobService processingJobService,
            SampleRepository sampleRepository,
            PatientRepository patientRepository,
            ProcessingJobRepository processingJobRepository) {
        this.sampleService = sampleService;
        this.processingJobService = processingJobService;
        this.sampleRepository = sampleRepository;
        this.patientRepository = patientRepository;
        this.processingJobRepository = processingJobRepository;
    }

    @GetMapping("/samples")
    public String sampleList(Model model) {
        List<SampleListRow> rows = sampleRepository.findAll().stream()
                .map(this::toRow)
                .toList();
        model.addAttribute("samples", rows);
        return "sample-list";
    }

    private SampleListRow toRow(Sample sample) {
        String patientName = patientRepository.findById(sample.patientId())
                .map(Patient::name)
                .orElse(sample.patientId());
        return new SampleListRow(sample.id(), patientName, sample.status().name());
    }

    @GetMapping("/samples/new")
    public String createSampleForm(Model model) {
        if (!model.containsAttribute("createSampleForm")) {
            model.addAttribute("createSampleForm", new CreateSampleForm());
        }
        return "create-sample";
    }

    @PostMapping("/samples")
    public String createSample(@ModelAttribute("createSampleForm") CreateSampleForm form, Model model) {
        SampleCreationResult result = sampleService.createSample(form.getPatientId(), form.getSampleType());

        if (result instanceof SampleCreationResult.Success success) {
            return "redirect:/samples/" + success.sample().id();
        } else if (result instanceof SampleCreationResult.Failure failure) {
            model.addAttribute("errorMessage", failure.message());
            return "create-sample";
        }

        throw new IllegalStateException("Unexpected SampleCreationResult: " + result);
    }

    @GetMapping("/samples/{id}")
    public String sampleDetails(@PathVariable String id, Model model) {
        Optional<Sample> sampleOpt = sampleRepository.findById(id);
        if (sampleOpt.isEmpty()) {
            model.addAttribute("notFound", true);
            return "sample-details";
        }

        Sample sample = sampleOpt.get();
        String patientName = patientRepository.findById(sample.patientId())
                .map(Patient::name)
                .orElse(sample.patientId());
        SampleStatus currentStatus = processingJobRepository.findBySampleId(sample.id())
                .map(ProcessingJob::currentStatus)
                .orElse(sample.status());

        List<SampleStageRow> stages = List.of(SampleStatus.values()).stream()
                .map(status -> new SampleStageRow(status.name(), stageState(status, currentStatus)))
                .toList();

        model.addAttribute("notFound", false);
        model.addAttribute("sampleId", sample.id());
        model.addAttribute("patientName", patientName);
        model.addAttribute("currentStatus", currentStatus.name());
        model.addAttribute("stages", stages);
        model.addAttribute("isFinalStage", currentStatus == SampleStatus.REPORT_GENERATED);
        return "sample-details";
    }

    @PostMapping("/samples/{id}/advance")
    public String advanceSample(@PathVariable String id) {
        processingJobService.advance(id);
        return "redirect:/samples/" + id;
    }

    private String stageState(SampleStatus stage, SampleStatus currentStatus) {
        int comparison = Integer.compare(stage.ordinal(), currentStatus.ordinal());
        if (comparison < 0) {
            return "completed";
        } else if (comparison == 0) {
            return "current";
        } else {
            return "remaining";
        }
    }
}
