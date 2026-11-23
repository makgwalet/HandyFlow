// security/api/CheckpointAdminController.java
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
import za.co.handyflow.platform.security.application.internal.CheckpointAdminService;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.Row;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.UpdateRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/checkpoints")
@RequiredArgsConstructor
@Tag(name = "Security - Checkpoints")
public class CheckpointAdminController {

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

    private final CheckpointAdminService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Every checkpoint with 30-day scan count, last scan and active-route use; optional site, inactive on request")
    public ResponseEntity<ApiResponse<List<Row>>> list(@RequestParam(required = false) UUID siteId,
                                                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        featureGuard.requireModule("security");
        if (siteId != null) site(siteId);
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, includeInactive, allowed())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Edit a checkpoint's name, description, NFC tag, Bluetooth beacon or active flag (a deactivated checkpoint cannot be scanned)")
    public ResponseEntity<ApiResponse<Row>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.CHECKPOINT, id);
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
