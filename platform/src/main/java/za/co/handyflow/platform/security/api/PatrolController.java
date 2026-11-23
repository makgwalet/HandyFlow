// security/api/PatrolController.java
package za.co.handyflow.platform.security.api;

import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.PatrolOverviewService;
import za.co.handyflow.platform.security.dto.PatrolDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/patrols")
@RequiredArgsConstructor
@Tag(name = "Security - Patrols")
public class PatrolController {

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** The sites the user may see: null means the whole company. */
    private java.util.Set<UUID> allowed() {
        return za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
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

    private final PatrolOverviewService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Patrol rounds that were due in [from, to) (at most 31 days), optional site and status")
    public ResponseEntity<ApiResponse<List<RoundRow>>> list(@RequestParam Instant from, @RequestParam Instant to,
                                                            @RequestParam(required = false) UUID siteId,
                                                            @RequestParam(required = false) String status) {
        featureGuard.requireModule("security");
        if (siteId != null) site(siteId);
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), from, to, siteId, status, allowed())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "One patrol round with its route's checkpoints and who scanned each, when")
    public ResponseEntity<ApiResponse<RoundDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.PATROL_ROUND, id);
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record that a supervisor has seen a missed or partial round, with a note")
    public ResponseEntity<ApiResponse<RoundDetail>> acknowledge(@PathVariable UUID id, @Valid @RequestBody AcknowledgeRequest req) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.PATROL_ROUND, id);
        return ResponseEntity.ok(ApiResponse.success(service.acknowledge(
                TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), req.note())));
    }
}
