// security/api/ControlRoomController.java

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
import za.co.handyflow.platform.security.application.internal.ControlRoomService;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.model.Dispatch;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * ControlRoomController — alarm event ingestion, triage queue, armed-response
 * dispatch, and SLA tracking.
 *
 * Two access patterns:
 *
 * 1. Ingestion (POST /alarm-events) — this is a webhook target for external
 *    systems (alarm panel cloud APIs, CCTV motion detection, a future panic
 *    button hardware integration). It currently sits behind the standard
 *    tenant JwtAuthFilter like everything else in this module; a production
 *    deployment integrating real third-party alarm panels would need either
 *    a per-tenant API key auth scheme or IP allowlisting for this specific
 *    endpoint, since alarm panels can't carry a user's JWT. That's flagged
 *    here rather than solved — it's an infra decision for whoever wires the
 *    first real panel integration, not a code change.
 *
 * 2. Everything else (triage, dispatch, SLA) — supervisor/control-room
 *    operator actions, gated USER_UPDATE.
 */
@Tag(name = "Security - Control Room (Phase 3)")
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class ControlRoomController {

    private final ControlRoomService controlRoomService;

    // ── Ingestion ──────────────────────────────────────────────────────────────

    @PostMapping("/alarm-events")
    @Operation(
            summary = "Ingest a new alarm event",
            description = """
            Webhook target for alarm panels, CCTV motion detection, drone
            observations, or manual entry. siteId is optional — some sources
            (a guard's duress trigger) aren't tied to a fixed site at the
            moment of ingestion. rawPayload stores the verbatim webhook body
            for reference, never parsed back out as a source of truth.
            """)
    public ResponseEntity<ApiResponse<AlarmEvent>> ingest(
            @Valid @RequestBody IngestAlarmEventRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(controlRoomService.ingest(tenantId, req)));
    }

    // ── Triage Queue ───────────────────────────────────────────────────────────

    @GetMapping("/alarm-events")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Open triage queue — events not yet resolved or dismissed")
    public ResponseEntity<ApiResponse<Page<AlarmEvent>>> getOpenQueue(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.getOpenQueue(tenantId, pageable)));
    }

    @GetMapping("/sites/{siteId}/alarm-events")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "All alarm events for a site (full history, not just open queue)")
    public ResponseEntity<ApiResponse<Page<AlarmEvent>>> getEventsForSite(
            @PathVariable UUID siteId, Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.getEventsForSite(tenantId, siteId, pageable)));
    }

    @PostMapping("/alarm-events/{id}/triage")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Triage an alarm event",
            description = "Control room operator reviews and confirms/adjusts severity. " +
                    "Moves status NEW → TRIAGED.")
    public ResponseEntity<ApiResponse<AlarmEvent>> triage(
            @PathVariable UUID id,
            @Valid @RequestBody TriageAlarmEventRequest req) {
        TenantId tenantId  = TenantContext.getTenantIdAsObject();
        UUID     operatorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.triage(tenantId, id, operatorId, req)));
    }

    @PostMapping("/alarm-events/{id}/false-alarm")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Dismiss an alarm event as a false alarm — no dispatch needed")
    public ResponseEntity<ApiResponse<AlarmEvent>> markFalseAlarm(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.markFalseAlarm(tenantId, id)));
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    @PostMapping("/alarm-events/{id}/dispatch")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Dispatch a response unit to an alarm event",
            description = "Creates a Dispatch row with dispatchedAt set to now. " +
                    "Moves the alarm event status to DISPATCHED.")
    public ResponseEntity<ApiResponse<DispatchResponse>> dispatch(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDispatchRequest req) {
        TenantId tenantId  = TenantContext.getTenantIdAsObject();
        UUID     operatorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                controlRoomService.dispatch(tenantId, id, operatorId, req)));
    }

    @PostMapping("/dispatches/{id}/arrive")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Record the dispatched unit's arrival on-scene",
            description = "Drives the response-time SLA metric (dispatchedAt → arrivedAt).")
    public ResponseEntity<ApiResponse<DispatchResponse>> recordArrival(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.recordArrival(tenantId, id)));
    }

    @PatchMapping("/dispatches/{id}/resolve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Resolve a dispatch",
            description = """
            Drives the resolution-time SLA metric (dispatchedAt → resolvedAt).
            If outcome is RESOLVED or ESCALATED, automatically creates a linked
            Incident using the alarm event's site/severity/location — no need
            to re-enter the same context in a separate incident report.
            FALSE_ALARM and NO_ACTION_NEEDED outcomes do not create an incident.
            """)
    public ResponseEntity<ApiResponse<DispatchResponse>> resolveDispatch(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDispatchRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.resolveDispatch(tenantId, id, req)));
    }

    // ── Duress Trigger ─────────────────────────────────────────────────────────

    @PostMapping("/duress")
    @Operation(
            summary = "Trigger a duress event (panic)",
            description = """
            The highest-priority alarm source — severity is hard-set to
            CRITICAL regardless of any input, and no triage step is required
            before dispatch. Per Part 9.4, this deliberately has no
            USER_UPDATE gate beyond standard authentication: any guard in
            distress must be able to trigger this without needing elevated
            permissions, since the whole point is sub-second alerting under
            pressure. Optionally links to a protection detail if the duress
            occurred during a CP engagement.
            """)
    public ResponseEntity<ApiResponse<AlarmEvent>> triggerDuress(
            @Valid @RequestBody TriggerDuressRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(controlRoomService.triggerDuress(tenantId, req)));
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @GetMapping("/dispatches/open")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "All currently open (unresolved) dispatches — the active-response view")
    public ResponseEntity<ApiResponse<List<Dispatch>>> getOpenDispatches() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.getOpenDispatches(tenantId)));
    }

    @GetMapping("/alarm-events/{id}/dispatches")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "All dispatches for an alarm event (history, including re-dispatches)")
    public ResponseEntity<ApiResponse<List<Dispatch>>> getDispatchesForEvent(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                controlRoomService.getDispatchesForEvent(id)));
    }
}
