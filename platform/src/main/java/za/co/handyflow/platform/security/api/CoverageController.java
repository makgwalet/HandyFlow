// security/api/CoverageController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CoverageService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Coverage")
public class CoverageController {

    public record PostRequest(@NotBlank String name, @NotEmpty List<Integer> weekdays, @NotNull LocalTime start, @NotNull LocalTime end, @NotNull Integer minGuards) {}

    private final CoverageService coverage;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }


    @GetMapping("/api/v1/security/coverage")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Where the roster falls short of what sites need, today and the next days (default 7, at most 14)",
            description = "Measured against site posts. A site with no posts has no demand, so nothing is reported for it. Cancelled, missed and pulled shifts do not count as cover.")
    public ResponseEntity<ApiResponse<CoverageService.Board>> board(@RequestParam(required = false) Integer days, @RequestParam(required = false) UUID siteId) {
        featureGuard.requireModule("security");
        // A limited user sees the posts of their own sites only; asking for another site is "not found".
        java.util.Set<UUID> allowed = za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
        if (siteId != null) za.co.handyflow.platform.security.application.internal.SiteScopeRules.requireSite(allowed, siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", coverage.board(TenantContext.getTenantIdAsObject(), siteId, days, allowed)));
    }

    @GetMapping("/api/v1/security/sites/{siteId}/coverage-posts")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    public ResponseEntity<ApiResponse<List<CoverageService.PostView>>> posts(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", coverage.posts(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/coverage-posts")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Say what a site needs: a named post, weekdays (1 = Monday), times (SAST) and the number of guards",
            description = "An end time at or before the start time runs past midnight.")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> addPost(@PathVariable UUID siteId, @Valid @RequestBody PostRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        UUID id = coverage.addPost(TenantContext.getTenantIdAsObject(), siteId, TenantContext.getCurrentUserId(),
                r.name(), r.weekdays(), r.start(), r.end(), r.minGuards());
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of("id", id)));
    }

    @DeleteMapping("/api/v1/security/sites/{siteId}/coverage-posts/{postId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Stop measuring coverage against a post (kept for history)")
    public ResponseEntity<ApiResponse<Void>> removePost(@PathVariable UUID siteId, @PathVariable UUID postId) {
        featureGuard.requireModule("security");
        site(siteId);
        coverage.deactivatePost(TenantContext.getTenantIdAsObject(), siteId, postId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
