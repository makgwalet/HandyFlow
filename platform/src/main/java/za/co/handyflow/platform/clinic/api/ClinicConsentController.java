package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicConsentService;
import za.co.handyflow.platform.clinic.dto.ConsentEventResponse;
import za.co.handyflow.platform.clinic.dto.ConsentStatusResponse;
import za.co.handyflow.platform.clinic.dto.RecordConsentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** FIX: "no POPIA consent tracking" gap. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}/consent")
@RequiredArgsConstructor
@Tag(name = "Clinic Consent", description = "POPIA consent tracking per patient")
public class ClinicConsentController {

    private final ClinicConsentService consentService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Current consent status for each tracked consent type")
    public ResponseEntity<ApiResponse<List<ConsentStatusResponse>>> getStatus(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                consentService.getConsentStatus(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Full consent event history for a patient")
    public ResponseEntity<ApiResponse<List<ConsentEventResponse>>> getHistory(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                consentService.getConsentHistory(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Record a consent grant or revocation")
    public ResponseEntity<ApiResponse<ConsentEventResponse>> recordConsent(
            @PathVariable UUID patientId,
            @Valid @RequestBody RecordConsentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Consent recorded",
                consentService.recordConsent(TenantContext.getTenantIdAsObject(), patientId, req)));
    }
}