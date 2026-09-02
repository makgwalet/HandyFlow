package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmTechnicianService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmTechnicianResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmTechnicianRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/technicians")
@RequiredArgsConstructor
public class FmTechnicianController {

    private final FmTechnicianService technicianService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmTechnicianResponse>>> getTechnicians(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(technicianService.getTechnicians(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmTechnicianResponse>> createTechnician(@Valid @RequestBody UpsertFmTechnicianRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Technician added",
                technicianService.createTechnician(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmTechnicianResponse>> updateTechnician(@PathVariable UUID id, @Valid @RequestBody UpsertFmTechnicianRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Technician updated",
                technicianService.updateTechnician(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmTechnicianResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Technician deactivated",
                technicianService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmTechnicianResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Technician reactivated",
                technicianService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTechnician(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        technicianService.deleteTechnician(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Technician deleted", null));
    }
}
