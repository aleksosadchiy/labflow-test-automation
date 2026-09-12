package com.mocklab.web;

import com.mocklab.domain.Patient;
import com.mocklab.domain.Sample;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.service.SampleCreationResult;
import com.mocklab.service.SampleService;
import com.mocklab.web.form.CreateSampleForm;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Sample List and Create Sample screens. {@code GET /samples} shows every
 * registered sample with its patient name and current status
 * (functional-spec.md §2.2). {@code GET /samples/new} and {@code POST
 * /samples} back the Create Sample form (functional-spec.md §2.3),
 * delegating validation and persistence to {@link SampleService}.
 */
@Controller
public class SampleWebController {

    private final SampleService sampleService;
    private final SampleRepository sampleRepository;
    private final PatientRepository patientRepository;

    public SampleWebController(
            SampleService sampleService,
            SampleRepository sampleRepository,
            PatientRepository patientRepository) {
        this.sampleService = sampleService;
        this.sampleRepository = sampleRepository;
        this.patientRepository = patientRepository;
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
}
