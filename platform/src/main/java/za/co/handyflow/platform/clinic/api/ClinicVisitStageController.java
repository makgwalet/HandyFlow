package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicVisitStageService;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.SetStagesRequest;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stages;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/clinic/visit-types/{visitType}/stages")
@RequiredArgsConstructor
@Tag(name = "Clinic visit-type required stages", description = "Which consultation stages each visit type requires before signing")
public class ClinicVisitStageController {

    private final ClinicVisitStageService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "The stages this visit type requires for this practice (its own, else the platform default, else Symptoms + Diagnosis)")
    public ResponseEntity<ApiResponse<Stages>> get(@PathVariable String visitType) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.get(TenantContext.getTenantIdAsObject(), visitType)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Set this practice's required stages for the visit type (all four stages)")
    public ResponseEntity<ApiResponse<Stages>> set(@PathVariable String visitType, @RequestBody SetStagesRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.set(TenantContext.getTenantIdAsObject(), visitType, body == null ? null : body.stages())));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Drop this practice's own stages and go back to the platform default")
    public ResponseEntity<ApiResponse<Stages>> clear(@PathVariable String visitType) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.clear(TenantContext.getTenantIdAsObject(), visitType)));
    }
}
