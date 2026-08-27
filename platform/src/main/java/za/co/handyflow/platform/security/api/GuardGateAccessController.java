// security/api/GuardGateAccessController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.GateAccessService;
import za.co.handyflow.platform.security.dto.GateRegisterEntryResponse;
import za.co.handyflow.platform.security.dto.LogArrivalRequest;
import za.co.handyflow.platform.security.dto.LogExitRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * FIX: mandatory correction from the mobile addendum. Guard-facing gate
 * entry/exit endpoints live here, under /api/v1/guard/**, secured by
 * GuardJwtFilter + SECURITY_GUARD — NOT under /api/v1/security/** (the
 * original plan's own §8 sketch put them on the tenant-JWT surface,
 * which a guard's mobile app has no token for at all). Confirmed via
 * direct investigation that this correction was genuinely necessary:
 * the still-current CheckpointScanController lives on the OLD
 * tenant-JWT + USER_UPDATE surface and hasn't been migrated to
 * GuardJwtFilter yet — copying that as "the existing convention"
 * without this correction would have built the same now-wrong pattern
 * a third time.
 * <p>
 * TenantContext.getTenantIdAsObject() works correctly here — GuardJwtFilter
 * populates TenantContext from the guard token's own tenantId claim,
 * confirmed directly in that filter's implementation.
 */
@RestController
@RequestMapping("/api/v1/guard/gate")
@RequiredArgsConstructor
@Tag(name = "Guard - Gate Access", description = "Guard-facing gate entry/exit logging (mobile)")
public class GuardGateAccessController {

    private final GateAccessService gateAccessService;

    @PostMapping("/entries")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Log an arrival at a gate — visitor, contractor, delivery, or staff vehicle",
            description = "The logging guard's identity is resolved server-side from the device's " +
                    "currently open DeviceSession — never trusted from the request body. " +
                    "Fails with 403/NO_OPEN_SESSION if the device has no open session (guard not clocked in).")
    public ResponseEntity<ApiResponse<GateRegisterEntryResponse>> logArrival(
            @Valid @RequestBody LogArrivalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Entry logged",
                gateAccessService.logArrival(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/entries/{entryId}/exit")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Log a departure — closes out an ON_SITE or OVERSTAYED entry",
            description = "Same session-resolved identity posture as logArrival(). Can be closed " +
                    "by any guard with an open session at this site, not necessarily the same " +
                    "guard who logged the arrival (shift handover).")
    public ResponseEntity<ApiResponse<GateRegisterEntryResponse>> logExit(
            @PathVariable UUID entryId,
            @Valid @RequestBody LogExitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Exit logged",
                gateAccessService.logExit(TenantContext.getTenantIdAsObject(), entryId, request)));
    }

    // ── Evidence attachment ───────────────────────────────────────────────────

    @PostMapping(value = "/entries/{entryId}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Attach evidence to an entry — ID scan, license disc photo, or general photo",
            description = "docType must be ID_DOCUMENT, VEHICLE_DISC, or GENERAL_PHOTO — the three " +
                    "genuinely distinct capture types (mobile addendum rule). Goes through the " +
                    "shared evidence module, not a legacy base64 pattern. deviceHardwareId " +
                    "resolves the attaching guard's identity the same way logArrival()/logExit() do.")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.evidence.dto.EvidenceResponse>> attachEvidence(
            @PathVariable UUID entryId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("docType") String docType,
            @RequestParam("deviceHardwareId") String deviceHardwareId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attachment uploaded",
                gateAccessService.attachEvidence(TenantContext.getTenantIdAsObject(), entryId,
                        file, docType, deviceHardwareId)));
    }
}