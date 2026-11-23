// security/api/PatrolRouteController.java

package za.co.handyflow.platform.security.api;

import za.co.handyflow.platform.security.application.internal.SiteScopeService;
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
 * <p>
 * FIX: flagged repeatedly across this session's own permission-fix passes
 * (Fuel 5.2, Accounting 8.1, Fleet 12.1, Guard/Site/Shift 7.0/1.7) as a
 * confirmed still-open bug, finally addressed here. Turned out MORE
 * serious than the earlier flagging described: getRoutesForSite() and
 * getRoundsForShift() had NO @PreAuthorize at all — not merely the wrong
 * generic tier, genuinely unguarded beyond the base tenant JWT
 * requirement. Same SECURITY_READ/SECURITY_MANAGE two-tier convention
 * as every other Security controller fixed this session — no delete
 * endpoint here, so no SECURITY_ADMIN tier needed.
 * <p>
 * Routes are configured per site by a supervisor. Rounds are generated
 * automatically when a shift starts (see DeviceSessionService.openSession()
 * → PatrolRoundService.generateRoundsForShift()) and are read-only from
 * this controller — they're materialized state, not something a client
 * creates directly.
 */
@Tag(name = "Security - Patrol Routes & Rounds")
@RestController
@RequestMapping("/api/v1/security/patrol-routes")
@RequiredArgsConstructor
public class PatrolRouteController {

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a list whose site is optional: a user limited to some sites must name one of them, the whole company may leave it out. */
    private void choice(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChoice(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a view that spans every site and cannot be narrowed yet: only a user with the whole company may use it. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    /** A record that belongs to a site: one at a site outside the user's scope answers "not found". */
    private void child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind kind, UUID id) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), kind, id);
    }

    /** For a site that is optional in the request: leaving it out means the whole company. */
    private void optionalSite(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkOptionalSite(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    private final PatrolRoundService patrolRoundService;

    // ── Route CRUD ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List active patrol routes for a site")
    public ResponseEntity<ApiResponse<List<PatrolRoute>>> getRoutesForSite(
            @RequestParam UUID siteId) {
        site(siteId);
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                patrolRoundService.getRoutesForSite(tenantId, siteId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
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
        site(req.siteId());
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        PatrolRoute route = patrolRoundService.createRoute(
                tenantId, req.siteId(), req.name(),
                req.intervalMinutes(), req.toleranceMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(route));
    }

    @PostMapping("/{routeId}/checkpoints")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Add a checkpoint to a patrol route",
            description = "sequence determines scan order within the route. " +
                    "Call once per checkpoint to build the full route.")
    public ResponseEntity<ApiResponse<Void>> addCheckpointToRoute(
            @PathVariable UUID routeId,
            @Valid @RequestBody AddRouteCheckpointRequest req) {
        child(SiteScopeService.Kind.PATROL_ROUTE, routeId);
        patrolRoundService.addCheckpointToRoute(routeId, req.checkpointId(), req.sequence());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    // ── Round Queries (live status, read-only) ─────────────────────────────────

    @GetMapping("/rounds")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
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
        child(SiteScopeService.Kind.SHIFT, shiftId);
        return ResponseEntity.ok(ApiResponse.success(
                patrolRoundService.getRoundsForShift(TenantContext.getTenantIdAsObject(), shiftId)));
    }
}