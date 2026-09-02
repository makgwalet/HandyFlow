package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvDelegateService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.dto.DelegateResponse;
import za.co.handyflow.platform.trainingprovider.dto.UpsertDelegateRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Delegates", description = "People nominated by clients to attend training")
public class TrainProvDelegateController {

    private final TrainProvDelegateService delegateService;
    private final FeatureGuard featureGuard;

    @GetMapping("/delegates")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<DelegateResponse>>> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                delegateService.list(TenantContext.getTenantIdAsObject(), clientId, search, pageable).map(this::toResponse)));
    }

    @GetMapping("/delegates/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegateResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(delegateService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{clientId}/delegates")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Nominate a new delegate for a client")
    public ResponseEntity<ApiResponse<DelegateResponse>> create(@PathVariable UUID clientId, @Valid @RequestBody UpsertDelegateRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvDelegate delegate = delegateService.create(TenantContext.getTenantIdAsObject(), clientId, req.fullName(),
                req.idNumber(), req.email(), req.phone(), req.jobTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Delegate added", toResponse(delegate)));
    }

    @PutMapping("/delegates/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegateResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpsertDelegateRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvDelegate delegate = delegateService.update(TenantContext.getTenantIdAsObject(), id, req.fullName(),
                req.idNumber(), req.email(), req.phone(), req.jobTitle());
        return ResponseEntity.ok(ApiResponse.success("Delegate updated", toResponse(delegate)));
    }

    @PostMapping("/delegates/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegateResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(delegateService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/delegates/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<DelegateResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(delegateService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/delegates/{id}")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Soft-delete a delegate — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        delegateService.softDelete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Delegate deleted", null));
    }

    private DelegateResponse toResponse(TrainProvDelegate d) {
        return new DelegateResponse(d.getId(), d.getClientId(), d.getDelegateNumber(), d.getFullName(), d.getIdNumber(),
                d.getEmail(), d.getPhone(), d.getJobTitle(), d.getStatus(), d.getCreatedAt());
    }
}
