package za.co.handyflow.platform.facilities.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityTechnicianService;
import za.co.handyflow.platform.facilities.dto.TechnicianResponse;
import za.co.handyflow.platform.facilities.dto.UpsertTechnicianRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/technicians")
@RequiredArgsConstructor
public class FacilityTechnicianController {

    private final FacilityTechnicianService technicianService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<TechnicianResponse>>> getTechnicians(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(technicianService.getTechnicians(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> createTechnician(@Valid @RequestBody UpsertTechnicianRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Technician added",
                technicianService.createTechnician(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> updateTechnician(@PathVariable UUID id, @Valid @RequestBody UpsertTechnicianRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Technician updated",
                technicianService.updateTechnician(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Technician deactivated",
                technicianService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Technician reactivated",
                technicianService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTechnician(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        technicianService.deleteTechnician(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Technician deleted", null));
    }
}
