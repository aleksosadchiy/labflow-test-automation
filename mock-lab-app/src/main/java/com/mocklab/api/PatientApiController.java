package com.mocklab.api;

import com.mocklab.api.dto.PatientRequest;
import com.mocklab.api.dto.PatientResponse;
import com.mocklab.domain.Patient;
import com.mocklab.service.PatientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * REST surface for patients (functional-spec.md §2.2, technical-considerations.md §2.3).
 *
 * <p>Deliberately thin: every rule lives in {@link PatientService}, and every
 * failure is turned into the standard error body by the {@code /api/**} error
 * resolver. Only the two routes below exist, so any other verb on these paths
 * is a 405. Patient names and request bodies are never logged.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientApiController {

    private final PatientService patientService;

    public PatientApiController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    public ResponseEntity<PatientResponse> register(@RequestBody PatientRequest request) {
        Patient patient = patientService.register(request.name());
        URI location = UriComponentsBuilder.fromPath("/api/patients/{id}").build(patient.id());
        return ResponseEntity.created(location).body(PatientResponse.from(patient));
    }

    @GetMapping("/{id}")
    public PatientResponse find(@PathVariable String id) {
        return PatientResponse.from(patientService.find(id));
    }
}
