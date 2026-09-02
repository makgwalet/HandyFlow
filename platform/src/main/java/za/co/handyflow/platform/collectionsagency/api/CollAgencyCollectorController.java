package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyCollectorService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCollector;
import za.co.handyflow.platform.collectionsagency.dto.CollectorResponse;
import za.co.handyflow.platform.collectionsagency.dto.CreateCollectorRequest;
import za.co.handyflow.platform.collectionsagency.dto.UpdateCollectorRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Individual-collector registration under the Debt Collectors Act — a
 * separate registration from the firm's own (see CollAgencyProfile).
 * Registration-expiry alerting is CollAgencyNotificationScheduler's job
 * (Layer 5), not this controller's.
 */
@RestController
@RequestMapping("/api/v1/collections-agency/collectors")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Collectors", description = "Individually registered debt collectors")
public class CollAgencyCollectorController {

    private final CollAgencyCollectorService collectorService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<CollectorResponse>>> list() {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                collectorService.list(TenantContext.getTenantIdAsObject()).stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<CollectorResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(collectorService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Register a new individual collector")
    public ResponseEntity<ApiResponse<CollectorResponse>> create(@Valid @RequestBody CreateCollectorRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyCollector c = collectorService.create(TenantContext.getTenantIdAsObject(), req.userId(),
                req.fullName(), req.registrationNumber(), req.registrationExpiryDate(), req.email(), req.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Collector registered", toResponse(c)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<CollectorResponse>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateCollectorRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyCollector c = collectorService.update(TenantContext.getTenantIdAsObject(), id, req.fullName(),
                req.registrationNumber(), req.registrationExpiryDate(), req.email(), req.phone());
        return ResponseEntity.ok(ApiResponse.success("Collector updated", toResponse(c)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<CollectorResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Collector deactivated",
                toResponse(collectorService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<CollectorResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Collector reactivated",
                toResponse(collectorService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        collectorService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Collector deleted", null));
    }

    private CollectorResponse toResponse(CollAgencyCollector c) {
        return new CollectorResponse(c.getId(), c.getUserId(), c.getFullName(), c.getRegistrationNumber(),
                c.getRegistrationExpiryDate(), c.getEmail(), c.getPhone(), c.isActive());
    }
}
