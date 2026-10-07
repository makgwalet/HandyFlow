package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicObservationService;
import za.co.handyflow.platform.clinic.dto.ObservationDtos.ObservationRequest;
import za.co.handyflow.platform.clinic.dto.ObservationDtos.ObservationResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Observations. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}/observations")
@RequiredArgsConstructor
@Tag(name = "Clinic observations", description = "Vitals and measurements")
public class ClinicObservationController {

    private final ClinicObservationService service;

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Record one or more observations (max 50)")
    public ResponseEntity<ApiResponse<List<ObservationResponse>>> record(
            @PathVariable UUID patientId, @RequestBody List<ObservationRequest> body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Observations recorded",
                service.record(TenantContext.getTenantIdAsObject(), patientId, body)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List observations, newest first; filter by code, time range or consultation")
    public ResponseEntity<ApiResponse<List<ObservationResponse>>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID consultationId,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.list(TenantContext.getTenantIdAsObject(), patientId, code, from, to,
                        consultationId, includeVoided)));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Most recent value of each observation code")
    public ResponseEntity<ApiResponse<List<ObservationResponse>>> latest(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.latest(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Mark an observation entered in error (kept for audit, hidden from lists)")
    public ResponseEntity<ApiResponse<ObservationResponse>> voidObservation(
            @PathVariable UUID patientId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Observation voided",
                service.voidObservation(TenantContext.getTenantIdAsObject(), patientId, id)));
    }
}
