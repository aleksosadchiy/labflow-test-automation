package com.mocklab.api;

import com.mocklab.api.dto.SampleCreatedResponse;
import com.mocklab.api.dto.SampleRequest;
import com.mocklab.api.dto.SampleResponse;
import com.mocklab.exception.LabRuleException;
import com.mocklab.service.SampleCreationResult;
import com.mocklab.service.SampleQueryService;
import com.mocklab.service.SampleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * {@link SampleQueryService}, and every failure is turned into the standard
 * error body by the {@code /api/**} error resolver. Only the three routes below
 * exist, so any other verb on these paths is a 405. Sample and patient
 * identifiers and request bodies are never logged.
 */
@RestController
@RequestMapping("/api/samples")
public class SampleApiController {

    private final SampleService sampleService;
    private final SampleQueryService sampleQueryService;

    public SampleApiController(SampleService sampleService, SampleQueryService sampleQueryService) {
        this.sampleService = sampleService;
        this.sampleQueryService = sampleQueryService;
    }

    @PostMapping
    public ResponseEntity<SampleCreatedResponse> register(@RequestBody SampleRequest request) {
        SampleCreationResult result = sampleService.createSample(request.patientId(), request.sampleType());
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
}
