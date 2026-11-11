package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseReplenService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/replenishment")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Replenishment", description = "Min/max levels for pick faces and the tasks that refill them")
public class WhseReplenController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseReplenService service;
    private final FeatureGuard featureGuard;

    public record RuleRequest(@NotNull UUID clientId, @NotNull UUID itemId, @NotNull UUID locationId, @NotNull BigDecimal minQty, @NotNull BigDecimal maxQty, Boolean active) {}

    @GetMapping("/rules")
    @PreAuthorize(READ)
    @Operation(summary = "Replenishment rules with the stock at each face, what is on its way, and the reserve available")
    public ResponseEntity<ApiResponse<List<WhseReplenService.RuleRow>>> rules() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.rules(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/rules")
    @PreAuthorize(WRITE)
    @Operation(summary = "Create a rule, or change the levels of the one that exists for this item at this location")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> save(@Valid @RequestBody RuleRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.saveRule(TenantContext.getTenantIdAsObject(), req.clientId(), req.itemId(), req.locationId(), req.minQty(), req.maxQty(),
                req.active() == null || req.active(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Saved", Map.of("id", id)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.deleteRule(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/sync")
    @PreAuthorize(WRITE)
    @Operation(summary = "Creates replenishment tasks for every pick face below its minimum")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sync() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of("created", service.sync(TenantContext.getTenantIdAsObject()))));
    }
}
