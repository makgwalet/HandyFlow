// security/api/PatrolController.java
package za.co.handyflow.platform.security.api;

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

    private final PatrolOverviewService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Patrol rounds that were due in [from, to) (at most 31 days), optional site and status")
    public ResponseEntity<ApiResponse<List<RoundRow>>> list(@RequestParam Instant from, @RequestParam Instant to,
                                                            @RequestParam(required = false) UUID siteId,
                                                            @RequestParam(required = false) String status) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), from, to, siteId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "One patrol round with its route's checkpoints and who scanned each, when")
    public ResponseEntity<ApiResponse<RoundDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record that a supervisor has seen a missed or partial round, with a note")
    public ResponseEntity<ApiResponse<RoundDetail>> acknowledge(@PathVariable UUID id, @Valid @RequestBody AcknowledgeRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.acknowledge(
                TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), req.note())));
    }
}
