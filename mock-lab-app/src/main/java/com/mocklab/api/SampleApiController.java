package com.mocklab.api;

import com.mocklab.api.dto.ProgressResponse;
import com.mocklab.api.dto.ReportResponse;
import com.mocklab.api.dto.ResultResponse;
import com.mocklab.api.dto.SampleCreatedResponse;
import com.mocklab.api.dto.SampleRequest;
import com.mocklab.api.dto.SampleResponse;
import com.mocklab.api.dto.StatusRequest;
import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.SampleStatus;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.service.ProcessingJobService;
import com.mocklab.service.SampleCreationResult;
import com.mocklab.service.SampleQueryService;
import com.mocklab.service.SampleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST surface for samples (functional-spec.md §2.4, technical-considerations.md §2.3).
 *
 * <p>Deliberately thin: every rule lives in {@link SampleService} and
 * {@link SampleQueryService} and {@link ProcessingJobService}, and every failure
 * is turned into the standard error body by the {@code /api/**} error resolver.
 * Only the routes below exist, so any other verb on these paths is a 405.
 * Sample and patient identifiers and request bodies are never logged.
 */
@RestController
@RequestMapping("/api/samples")
public class SampleApiController {

    private final SampleService sampleService;
    private final SampleQueryService sampleQueryService;
    private final ProcessingJobService processingJobService;

    public SampleApiController(SampleService sampleService,
                               SampleQueryService sampleQueryService,
                               ProcessingJobService processingJobService) {
        this.sampleService = sampleService;
        this.sampleQueryService = sampleQueryService;
        this.processingJobService = processingJobService;
    }

    @PostMapping
    public ResponseEntity<SampleCreatedResponse> register(@RequestBody SampleRequest request) {
        SampleCreationResult result = sampleService.createSample(
                request.patientId(), request.sampleType(), request.orderId());
        if (result instanceof SampleCreationResult.Failure failure) {
            throw new LabRuleException(failure.kind(), failure.message());
        }
        SampleCreationResult.Success created = (SampleCreationResult.Success) result;
        URI location = UriComponentsBuilder.fromPath("/api/samples/{id}").build(created.sample().id());
        return ResponseEntity.created(location).body(SampleCreatedResponse.from(created));
    }

    @GetMapping
    public List<SampleResponse> list() {
        return sampleQueryService.list().stream().map(SampleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SampleResponse find(@PathVariable String id) {
        return SampleResponse.from(sampleQueryService.get(id));
    }

    /**
     * Requests the stage the sample should move to. Input problems are reported
     * first (missing status, then unknown status, both 400), then the service
     * checks the sample (404) and the transition (409), so a bad request never
     * reaches the sample lookup.
     */
    @PutMapping("/{id}/status")
    public SampleResponse changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        String requested = request.status();
        if (requested == null || requested.isBlank()) {
            throw new LabRuleException(FailureKind.VALIDATION, LabMessages.STATUS_REQUIRED);
        }
        SampleStatus target = SampleStatus.parse(requested)
                .orElseThrow(() -> new LabRuleException(FailureKind.VALIDATION, LabMessages.INVALID_STATUS));

        processingJobService.advanceTo(id, target);
        return SampleResponse.from(sampleQueryService.get(id));
    }

    @GetMapping("/{id}/progress")
    public ProgressResponse progress(@PathVariable String id) {
        return ProgressResponse.from(sampleQueryService.progress(id));
    }

    @GetMapping("/{id}/result")
    public ResultResponse result(@PathVariable String id) {
        return ResultResponse.from(sampleQueryService.result(id));
    }

    @GetMapping("/{id}/report")
    public ReportResponse report(@PathVariable String id) {
        return ReportResponse.from(sampleQueryService.report(id));
    }
}
