package za.co.handyflow.platform.fleet.api;

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
import za.co.handyflow.platform.fleet.application.internal.FleetIssueService;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;
import za.co.handyflow.platform.fleet.dto.CreateIssueRequest;
import za.co.handyflow.platform.fleet.dto.IssueResponse;
import za.co.handyflow.platform.fleet.dto.UpdateIssueStatusRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

/** The fleet issue (defect) register. Issues are raised automatically by failed inspections, or manually. */
@RestController
@RequestMapping("/api/v1/fleet/issues")
@RequiredArgsConstructor
@Tag(name = "Fleet - Issues", description = "Vehicle defects and maintenance issues")
public class FleetIssueController {

    private final FleetIssueService issueService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List issues, newest first; filter by vehicleId and/or status (OPEN, IN_PROGRESS, RESOLVED, DISMISSED)")
    public ResponseEntity<ApiResponse<Page<IssueResponse>>> list(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) IssueStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                issueService.list(TenantContext.getTenantIdAsObject(), vehicleId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<IssueResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(issueService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<IssueResponse>> create(@Valid @RequestBody CreateIssueRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Issue raised",
                issueService.create(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Move an issue through OPEN → IN_PROGRESS → RESOLVED / DISMISSED (resolved and dismissed issues can be reopened)")
    public ResponseEntity<ApiResponse<IssueResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateIssueStatusRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Issue updated",
                issueService.updateStatus(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }
}