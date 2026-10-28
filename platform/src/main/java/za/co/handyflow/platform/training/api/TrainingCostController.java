package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingCostService;
import za.co.handyflow.platform.training.domain.model.TrainingAuditRules;
import za.co.handyflow.platform.training.dto.TrainingCostDtos.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training/sessions/{sessionId}/costs")
@RequiredArgsConstructor
@Tag(name = "Training", description = "What a session cost")
public class TrainingCostController {

    private final TrainingCostService costs;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_REPORTS','TRAINING_ADMIN')")
    @Operation(summary = "The costs recorded against a session")
    public ResponseEntity<ApiResponse<SessionCosts>> list(@PathVariable UUID sessionId) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(costs.costs(TenantContext.getTenantIdAsObject(), sessionId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Record a cost against a session")
    public ResponseEntity<ApiResponse<SessionCosts>> add(@PathVariable UUID sessionId, @RequestBody CostRequest req) {
        featureGuard.requireModule("training");
        return ResponseEntity.status(201).body(ApiResponse.success("Recorded", costs.add(TenantContext.getTenantIdAsObject(), sessionId, req,
                TenantContext.getCurrentUserId(), LocalDate.now(TrainingAuditRules.ZONE))));
    }

    @DeleteMapping("/{costId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Remove a recorded cost")
    public ResponseEntity<ApiResponse<SessionCosts>> remove(@PathVariable UUID sessionId, @PathVariable UUID costId) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success("Removed", costs.remove(TenantContext.getTenantIdAsObject(), sessionId, costId)));
    }
}
