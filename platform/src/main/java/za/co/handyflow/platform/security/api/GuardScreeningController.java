// security/api/GuardScreeningController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.GuardScreeningService;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.dto.CreateScreeningRequest;
import za.co.handyflow.platform.security.dto.RecordScreeningResultRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * GuardScreeningController — vetting record CRUD and the scheduling gate.
 *
 * All endpoints are supervisor-only (USER_UPDATE) — screening records contain
 * sensitive information about a guard (polygraph results, criminal record
 * checks) and are never guard-facing.
 *
 * The gate check (GET /{guardId}/gate) is advisory, not a hard block in
 * Phase 2 — see GuardScreeningService.checkScreeningGate() javadoc for why.
 * ShiftService and RotationService call the service method directly during
 * scheduling; this endpoint exists so the admin UI can show the same warning
 * before a supervisor manually assigns a shift.
 */
@Tag(name = "Security - Guard Screening")
@RestController
@RequestMapping("/api/v1/security/guards/{guardId}/screening")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_UPDATE')")
public class GuardScreeningController {

    private final GuardScreeningService screeningService;

    @GetMapping
    @Operation(summary = "Get full screening history for a guard",
            description = "Every record ever created, newest first — never overwritten, " +
                    "so a company can show a complete vetting history if challenged.")
    public ResponseEntity<ApiResponse<List<GuardScreeningRecord>>> getHistory(
            @PathVariable UUID guardId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                screeningService.getScreeningHistory(tenantId, guardId)));
    }

    @PostMapping
    @Operation(
            summary = "Create a new screening record (PENDING)",
            description = """
            Initiates a screening — e.g. "request polygraph before this guard
            works the Sandton Mall site." Result starts as PENDING until the
            external agency reports back via POST /{screeningId}/result.
            Updates the guard's screening_status rollup to PENDING immediately.
            """)
    public ResponseEntity<ApiResponse<GuardScreeningRecord>> createScreening(
            @PathVariable UUID guardId,
            @Valid @RequestBody CreateScreeningRequest req) {
        TenantId tenantId    = TenantContext.getTenantIdAsObject();
        UUID     supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                screeningService.createScreening(tenantId, guardId, req, supervisorId)));
    }

    @PostMapping("/{screeningId}/result")
    @Operation(
            summary = "Record the result of a completed screening",
            description = """
            Called when the external agency's report comes back. Sets result
            (PASS/FAIL/INCONCLUSIVE), conductedBy/conductedAt for the record,
            and nextDueAt to drive the periodic re-screening alert (same
            pattern as PSiRA expiry). reportRef should point to an encrypted
            document store, never the report content itself.
            Recomputes the guard's screening_status rollup after saving.
            """)
    public ResponseEntity<ApiResponse<GuardScreeningRecord>> recordResult(
            @PathVariable UUID guardId,
            @PathVariable UUID screeningId,
            @Valid @RequestBody RecordScreeningResultRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                screeningService.recordResult(tenantId, screeningId, req)));
    }

    @GetMapping("/gate")
    @Operation(
            summary = "Check the screening gate before assigning a shift",
            description = """
            Returns a warning string if the guard has a FAILED or PENDING
            screening, or null if clear. Advisory only in Phase 2 — the
            supervisor decides whether to proceed. Phase 3 adds a site-level
            require_screening_clearance flag that makes this a hard block.
            """)
    public ResponseEntity<ApiResponse<String>> checkGate(@PathVariable UUID guardId) {
        return ResponseEntity.ok(ApiResponse.success(
                screeningService.checkScreeningGate(guardId)));
    }
}
