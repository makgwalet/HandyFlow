package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthDisposalService;
import za.co.handyflow.platform.earthmoving.dto.DisposalNoteRequest;
import za.co.handyflow.platform.earthmoving.dto.DisposalResponse;
import za.co.handyflow.platform.earthmoving.dto.RequestDisposalRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Taking machines out of the fleet. Evidence is attached through the documents endpoints with ownerKind DISPOSAL. */
@RestController
@RequestMapping("/api/v1/earthmoving/disposals")
@RequiredArgsConstructor
@Tag(name = "Earthmoving disposals")
public class EarthDisposalController {

    private final EarthDisposalService disposals;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Disposal requests, newest first; filter by status")
    public ResponseEntity<ApiResponse<List<DisposalResponse>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID assetId) {
        featureGuard.requireModule("earthmoving");
        var tenant = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Disposal requests", assetId == null ? disposals.list(tenant, status) : disposals.forAsset(tenant, assetId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<DisposalResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Disposal request", disposals.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    @Operation(summary = "Ask to take a machine out of the fleet")
    public ResponseEntity<ApiResponse<DisposalResponse>> request(@Valid @RequestBody RequestDisposalRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Disposal requested", disposals.request(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    public ResponseEntity<ApiResponse<DisposalResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) DisposalNoteRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Disposal approved", disposals.approve(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    public ResponseEntity<ApiResponse<DisposalResponse>> reject(@PathVariable UUID id, @RequestBody DisposalNoteRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Disposal rejected", disposals.reject(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    public ResponseEntity<ApiResponse<DisposalResponse>> cancel(@PathVariable UUID id, @RequestBody DisposalNoteRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Disposal cancelled", disposals.cancel(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Retire the machine once the request is approved, evidenced and nothing is running on it")
    public ResponseEntity<ApiResponse<DisposalResponse>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Machine retired", disposals.complete(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }
}
