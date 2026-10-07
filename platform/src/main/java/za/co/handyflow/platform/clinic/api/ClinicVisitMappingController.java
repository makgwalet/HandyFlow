package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicVisitMappingService;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Mapping;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.SetMappingRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/clinic/visit-types/{visitType}/groups")
@RequiredArgsConstructor
@Tag(name = "Clinic visit-type question groups", description = "Which question groups each visit type opens")
public class ClinicVisitMappingController {

    private final ClinicVisitMappingService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLINIC_CONTENT_ADMIN','CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "The groups this visit type opens for this practice (its own list, else the platform default)")
    public ResponseEntity<ApiResponse<Mapping>> get(@PathVariable String visitType) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.get(TenantContext.getTenantIdAsObject(), visitType)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Set this practice's own ordered list of groups for the visit type")
    public ResponseEntity<ApiResponse<Mapping>> set(@PathVariable String visitType, @RequestBody SetMappingRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.set(TenantContext.getTenantIdAsObject(), visitType, body == null ? null : body.groups())));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Drop this practice's own list and go back to the platform default")
    public ResponseEntity<ApiResponse<Mapping>> clear(@PathVariable String visitType) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.clear(TenantContext.getTenantIdAsObject(), visitType)));
    }
}
