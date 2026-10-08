package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicGrowthService;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Growth charts and their reference data (patch 0152, CLINIC-DEC-013). */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic growth", description = "Growth charts over approved reference data only")
public class ClinicGrowthController {

    private final ClinicGrowthService service;

    private static UUID tenant() { return TenantContext.getTenantIdAsObject().getValue(); }

    @GetMapping("/patients/{patientId}/growth")
    @PreAuthorize("hasAuthority('CLINIC_GROWTH_READ')")
    @Operation(summary = "Weight, height, head circumference and BMI by age. Z-scores and curves only from an ACTIVE approved reference set; otherwise the banner DATA NOT CLINICALLY APPROVED")
    public ResponseEntity<ApiResponse<GrowthChart>> chart(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.chart(tenant(), patientId)));
    }

    @GetMapping("/growth-reference")
    @PreAuthorize("hasAnyAuthority('CLINIC_CONTENT_ADMIN','CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "Reference sets and their review status")
    public ResponseEntity<ApiResponse<List<SetRow>>> sets() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.sets(tenant())));
    }

    @PostMapping("/growth-reference")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Load a reference set as DRAFT (L, M, S per age). Not served to clinicians until activated")
    public ResponseEntity<ApiResponse<SetRow>> create(@RequestBody CreateSetRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Draft created", service.create(tenant(), UserContext.getCurrentUserId(), body)));
    }

    @PostMapping("/growth-reference/{id}/submit")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "DRAFT to CLINICAL_REVIEW")
    public ResponseEntity<ApiResponse<SetRow>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Sent for review", service.submitForReview(tenant(), id)));
    }

    @PostMapping("/growth-reference/{id}/approve")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "Reviewer confirms the set matches its cited source: CLINICAL_REVIEW to APPROVED")
    public ResponseEntity<ApiResponse<SetRow>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Approved", service.approve(tenant(), UserContext.getCurrentUserId(), id)));
    }

    @PostMapping("/growth-reference/{id}/send-back")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "Return a set under review or approved to DRAFT for correction")
    public ResponseEntity<ApiResponse<SetRow>> sendBack(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Returned to draft", service.sendBack(tenant(), id)));
    }

    @PostMapping("/growth-reference/{id}/activate")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "APPROVED to ACTIVE by a different person from the reviewer; retires the set it replaces")
    public ResponseEntity<ApiResponse<SetRow>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Activated", service.activate(tenant(), UserContext.getCurrentUserId(), id)));
    }

    @PostMapping("/growth-reference/{id}/retire")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "ACTIVE to RETIRED: charts go back to DATA NOT CLINICALLY APPROVED")
    public ResponseEntity<ApiResponse<SetRow>> retire(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Retired", service.retire(tenant(), id)));
    }
}
