// security/api/ArmouryController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ArmouryService;
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * ArmouryController — firearm register and the witnessed issue/return workflow.
 *
 * Everything except getById/getHistory/getIssuedToGuard requires USER_UPDATE —
 * registering firearms, updating licenses, and issuing/returning are all
 * supervisor-level actions. Issue/return could in principle be guard-facing
 * (called from the Shield app at shift start/end alongside resource custody
 * checkout), but is kept admin-gated for now since the witness-validation
 * logic needs a trusted caller — Phase 3.5 can move this behind GuardJwtFilter
 * once witness confirmation has its own PIN-based step in the app.
 */
@Tag(name = "Security - Armoury")
@RestController
@RequestMapping("/api/v1/security/armoury")
@RequiredArgsConstructor
public class ArmouryController {

    private final ArmouryService armouryService;

    // ── Register CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all active (non-decommissioned) firearms")
    public ResponseEntity<ApiResponse<Page<ArmouryResponse>>> getAll(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(armouryService.getAll(tenantId, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single firearm's current record")
    public ResponseEntity<ApiResponse<ArmouryResponse>> getById(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(armouryService.getById(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Register a new firearm in the armoury",
            description = "Serial number must be unique per tenant. License expiry is " +
                    "mandatory — issue() hard-blocks once it passes.")
    public ResponseEntity<ApiResponse<ArmouryResponse>> register(
            @Valid @RequestBody RegisterFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(armouryService.register(tenantId, req)));
    }

    @PutMapping("/{id}/license")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update a firearm's SAPS license details (e.g. after renewal)")
    public ResponseEntity<ApiResponse<ArmouryResponse>> updateLicense(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFirearmLicenseRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.updateLicense(tenantId, id, req)));
    }

    @PostMapping("/{id}/service")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Record a maintenance/service event for a firearm")
    public ResponseEntity<ApiResponse<ArmouryResponse>> recordService(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.recordService(tenantId, id, req)));
    }

    @PostMapping("/{id}/report-lost")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Report a firearm lost or stolen",
            description = "Sets status to LOST — terminal until manually resolved by support. " +
                    "Cannot be re-issued from this state.")
    public ResponseEntity<ApiResponse<ArmouryResponse>> reportLost(
            @PathVariable UUID id,
            @Valid @RequestBody ReportLostFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.reportLost(tenantId, id, req)));
    }

    @PostMapping("/{id}/decommission")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Permanently retire a firearm from service",
            description = "Cannot decommission a firearm that is currently ISSUED — return it first.")
    public ResponseEntity<ApiResponse<ArmouryResponse>> decommission(
            @PathVariable UUID id,
            @Valid @RequestBody DecommissionFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.decommission(tenantId, id, req)));
    }

    // ── Witnessed Issue / Return ───────────────────────────────────────────────

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Issue a firearm to a guard (mandatory two-person witness)",
            description = """
            Hard-blocks (not advisory) if: license expired, firearm not
            IN_ARMOURY, receiving guard's firearm competency expired/unset,
            or witness is the same guard / inactive / not found.
            Firearms Control Act compliance requires verifiable chain of
            custody — this is the one resource type where the witness step
            cannot be skipped, unlike the optional witness on the generic
            Phase 2 resource custody checkout.
            """)
    public ResponseEntity<ApiResponse<ArmouryResponse>> issue(
            @PathVariable UUID id,
            @Valid @RequestBody IssueFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.issue(tenantId, id, req)));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Return an issued firearm to the armoury (mandatory two-person witness)")
    public ResponseEntity<ApiResponse<ArmouryResponse>> returnFirearm(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.returnFirearm(tenantId, id, req)));
    }

    // ── History & Guard Queries ────────────────────────────────────────────────

    @GetMapping("/{id}/history")
    @Operation(summary = "Full issue/return history for a firearm",
            description = "Immutable audit trail — every event, witness, and timestamp " +
                    "since the firearm was registered.")
    public ResponseEntity<ApiResponse<List<ArmouryLog>>> getHistory(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.getHistory(tenantId, id)));
    }

    @GetMapping("/guard/{guardId}")
    @Operation(summary = "Firearms currently issued to a specific guard")
    public ResponseEntity<ApiResponse<List<ArmouryResponse>>> getIssuedToGuard(
            @PathVariable UUID guardId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                armouryService.getIssuedToGuard(tenantId, guardId)));
    }

    // ── Guard Firearm Competency ───────────────────────────────────────────────

    @PostMapping("/guard/{guardId}/competency")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Set a guard's firearm competency certificate",
            description = "Required before issue() will succeed for that guard. " +
                    "Same expiry-gating pattern as PSiRA grading.")
    public ResponseEntity<ApiResponse<Void>> setGuardCompetency(
            @PathVariable UUID guardId,
            @Valid @RequestBody SetFirearmCompetencyRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        armouryService.setGuardCompetency(tenantId, guardId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
