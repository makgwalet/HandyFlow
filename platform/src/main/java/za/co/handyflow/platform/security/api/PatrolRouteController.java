// security/api/PatrolRouteController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.PatrolRoundService;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.PatrolRoute;
import za.co.handyflow.platform.security.dto.AddRouteCheckpointRequest;
import za.co.handyflow.platform.security.dto.CreatePatrolRouteRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * PatrolRouteController — patrol route builder (admin) + round queries (live status).
 *
 * Routes are configured per site by a supervisor (USER_UPDATE authority).
 * Rounds are generated automatically when a shift starts (see
 * DeviceSessionService.openSession() → PatrolRoundService.generateRoundsForShift())
 * and are read-only from this controller — they're materialized state, not
 * something a client creates directly.
 */
@Tag(name = "Security - Patrol Routes & Rounds")
@RestController
@RequestMapping("/api/v1/security/patrol-routes")
@RequiredArgsConstructor
public class PatrolRouteController {

    private final PatrolRoundService patrolRoundService;

    // ── Route CRUD ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List active patrol routes for a site")
    public ResponseEntity<ApiResponse<List<PatrolRoute>>> getRoutesForSite(
            @RequestParam UUID siteId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                patrolRoundService.getRoutesForSite(tenantId, siteId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Create a patrol route for a site",
            description = """
            Defines how often the route's checkpoints must be patrolled
            (intervalMinutes) and the acceptable timing window either side
            (toleranceMinutes). e.g. intervalMinutes=120, toleranceMinutes=20
            means each round is expected every 2 hours, ±20 minutes.
            Add checkpoints to the route afterward via POST /{routeId}/checkpoints.
            """)
    public ResponseEntity<ApiResponse<PatrolRoute>> createRoute(
            @Valid @RequestBody CreatePatrolRouteRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        PatrolRoute route = patrolRoundService.createRoute(
                tenantId, req.siteId(), req.name(),
                req.intervalMinutes(), req.toleranceMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(route));
    }

    @PostMapping("/{routeId}/checkpoints")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Add a checkpoint to a patrol route",
            description = "sequence determines scan order within the route. " +
                    "Call once per checkpoint to build the full route.")
    public ResponseEntity<ApiResponse<Void>> addCheckpointToRoute(
            @PathVariable UUID routeId,
            @Valid @RequestBody AddRouteCheckpointRequest req) {
        patrolRoundService.addCheckpointToRoute(routeId, req.checkpointId(), req.sequence());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    // ── Round Queries (live status, read-only) ─────────────────────────────────

    @GetMapping("/rounds")
    @Operation(
            summary = "Get all patrol rounds for a shift",
            description = """
            Returns the round-by-round timeline for a shift — the proof-of-patrol
            artifact for supervisors and the client portal. Each round shows its
            expected window, actual scan progress, and status (EXPECTED,
            IN_PROGRESS, COMPLETE, PARTIAL, MISSED), plus the offSchedule flag
            for front-loading fraud detection (Part 6.6).
            """)
    public ResponseEntity<ApiResponse<List<PatrolRound>>> getRoundsForShift(
            @RequestParam UUID shiftId) {
        return ResponseEntity.ok(ApiResponse.success(
                patrolRoundService.getRoundsForShift(shiftId)));
    }
}
