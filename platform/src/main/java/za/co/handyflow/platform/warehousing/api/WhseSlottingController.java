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
import za.co.handyflow.platform.warehousing.application.internal.WhseSlottingService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/slotting")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Slotting", description = "Move fast-moving items nearer the front of the pick walk")
public class WhseSlottingController {

    private final WhseSlottingService service;
    private final FeatureGuard featureGuard;

    public record MoveRequest(@NotNull UUID clientId, @NotNull UUID itemId, @NotNull UUID fromLocationId, @NotNull UUID toLocationId) {}

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "ABC classes from picks over the last N days, and moves into empty locations nearer the front of the pick walk")
    public ResponseEntity<ApiResponse<WhseSlottingService.Overview>> suggestions(@RequestParam(defaultValue = "90") int days, @RequestParam(defaultValue = "5") int minGain,
                                                                                 @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.overview(TenantContext.getTenantIdAsObject(), days, minGain, limit)));
    }

    @PostMapping("/moves")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Create the move task for a suggestion; the stock moves when the task is done")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> move(@Valid @RequestBody MoveRequest r) {
        featureGuard.requireModule("warehousing");
        UUID id = service.createMove(TenantContext.getTenantIdAsObject(), r.clientId(), r.itemId(), r.fromLocationId(), r.toLocationId(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Move task created", Map.of("id", id)));
    }
}
