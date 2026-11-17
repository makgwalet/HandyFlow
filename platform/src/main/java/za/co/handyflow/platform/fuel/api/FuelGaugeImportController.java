package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelGaugeImportService;
import za.co.handyflow.platform.fuel.application.internal.FuelPosImportService;
import za.co.handyflow.platform.fuel.dto.GaugeImportDtos.*;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.CreatedKey;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.KeyRequest;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.KeyView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Review of tank-gauge readings, the labels a gauge system uses for tanks, and the keys. Not under /forecourt: a depot with no forecourt has gauges too.
 * A key made here is the same kind of key as for the pump/POS import: one key per sending system, valid for both feeds.
 */
@RestController
@RequestMapping("/api/v1/fuel/gauge-imports")
@RequiredArgsConstructor
@Tag(name = "Fuel Gauge Import Review", description = "Review tank-gauge readings, tank labels and import keys")
public class FuelGaugeImportController {

    private static final String REVIEW = "hasAnyAuthority('FUEL_MANAGE','FUEL_OPERATIONS_MANAGE')";
    private static final String KEYS = "hasAnyAuthority('FUEL_MANAGE')";

    private final FuelGaugeImportService gauges;
    private final FuelPosImportService keys;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<List<GaugeView>>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(gauges.list(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<GaugeView>> accept(@PathVariable UUID id, @Valid @RequestBody(required = false) AcceptRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Reading accepted as a dip reading",
                gauges.accept(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<GaugeView>> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Reading rejected",
                gauges.reject(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/aliases")
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<List<AliasView>>> aliases() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(gauges.listAliases(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/aliases")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<AliasView>> addAlias(@Valid @RequestBody AliasRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tank label saved",
                gauges.addAlias(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @DeleteMapping("/aliases/{id}")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<Void>> removeAlias(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        gauges.removeAlias(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Tank label removed", null));
    }

    @GetMapping("/keys")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<List<KeyView>>> keys() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(keys.listKeys(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/keys")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<CreatedKey>> createKey(@Valid @RequestBody KeyRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Key created. Copy it now; it cannot be shown again.",
                keys.createKey(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.name())));
    }

    @PostMapping("/keys/{id}/revoke")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<KeyView>> revokeKey(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Key revoked", keys.revokeKey(TenantContext.getTenantIdAsObject(), id)));
    }
}
