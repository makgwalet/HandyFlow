package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelOfflineConflictService;
import za.co.handyflow.platform.fuel.application.internal.FuelOfflineConflictService.Conflict;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** What the server refused when a phone's queued writes finally arrived. For supervisors; resolving one changes nothing but the list. */
@RestController
@RequestMapping("/api/v1/fuel/offline/conflicts")
@RequiredArgsConstructor
@Tag(name = "Fuel Offline", description = "Queued writes the server refused")
public class FuelOfflineConflictController {
    private static final String WHO = "hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE','FUEL_TRUCKSTOP_MANAGE','FUEL_SHIFT_SUPERVISE')";
    private final FuelOfflineConflictService conflicts;
    private final FeatureGuard featureGuard;

    public record ResolveRequest(@jakarta.validation.constraints.Size(max = 300) String note) {}

    @GetMapping
    @PreAuthorize(WHO)
    public ResponseEntity<ApiResponse<List<Conflict>>> open(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(conflicts.open(TenantContext.getTenantIdAsObject(), limit)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(WHO)
    public ResponseEntity<ApiResponse<Boolean>> resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(conflicts.resolve(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), req == null ? null : req.note())));
    }
}
