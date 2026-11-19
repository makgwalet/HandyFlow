package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.RegulatoryChangeService;
import za.co.handyflow.platform.legalcompliance.domain.model.ChangeStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryChangeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** What a new or repealed regulatory version means for this organisation, and the work to resolve it. */
@RestController
@RequestMapping("/api/v1/legalcompliance/changes")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Regulatory Change", description = "Impact of regulatory changes on decisions and obligations")
public class RegulatoryChangeController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_REVIEW = "hasAnyAuthority('LEGALCOMPLIANCE_CHANGE_REVIEW','LEGALCOMPLIANCE_ADMIN')";

    private final RegulatoryChangeService changes;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<ChangeResponse>>> list(@RequestParam(required = false) ChangeStatus status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        featureGuard.requireModule("legalcompliance");
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(changes.list(tenant(), status, pageable)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<SummaryResponse>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(changes.summary(tenant())));
    }

    @GetMapping("/impacts")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Impact worklist (default PENDING), oldest due date first")
    public ResponseEntity<ApiResponse<List<ImpactResponse>>> impacts(@RequestParam(defaultValue = "PENDING") ImpactStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(changes.listImpacts(tenant(), status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ChangeDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(changes.get(tenant(), id)));
    }

    @PostMapping("/impacts/{id}/resolve")
    @PreAuthorize(CAN_REVIEW)
    @Operation(summary = "Resolve one impact. ADDED: ACKNOWLEDGE after assessing. CHANGED: CONFIRM_APPLICABLE | NO_LONGER_APPLICABLE | ACKNOWLEDGE. REMOVED: RETIRE_OBLIGATION | KEEP_OBLIGATION | ACKNOWLEDGE.")
    public ResponseEntity<ApiResponse<ImpactResponse>> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Impact resolved",
                changes.resolve(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req)));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
