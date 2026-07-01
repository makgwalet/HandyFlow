// security/api/CloseProtectionController.java

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
import za.co.handyflow.platform.security.application.internal.CloseProtectionService;
import za.co.handyflow.platform.security.domain.model.AuditEvent;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * CloseProtectionController — VIP/Close Protection module core (Part 9).
 *
 * CONFIDENTIALITY (Part 9.3):
 * Every endpoint in this controller requires VIP_DETAIL_ACCESS in addition
 * to (not instead of) the existing USER_READ/USER_UPDATE checks elsewhere in
 * the module — a site supervisor who can see all guards and incidents
 * should NOT automatically see principal names, itineraries, or threat
 * assessments unless explicitly assigned to that detail.
 *
 * This is a hard requirement at the class level (@PreAuthorize on the class
 * applies to every method) rather than spot-checked per endpoint, so a new
 * endpoint added later can't accidentally ship without the gate.
 *
 * IMPORTANT — operational note for whoever manages roles:
 * VIP_DETAIL_ACCESS does not exist anywhere else in the codebase yet. It
 * must be added as an assignable permission in whatever admin UI manages
 * role/permission grants (the same place USER_UPDATE etc. are assigned) —
 * this controller only enforces the check, it doesn't create the permission
 * itself. Until a role is granted VIP_DETAIL_ACCESS, nobody (including
 * tenant admins) can reach any endpoint here.
 */
@Tag(name = "Security - Close Protection")
@RestController
@RequestMapping("/api/v1/security/cp")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VIP_DETAIL_ACCESS')")
public class CloseProtectionController {

    private final CloseProtectionService cpService;

    // ── Principals ─────────────────────────────────────────────────────────────

    @GetMapping("/principals")
    @Operation(summary = "List all active principals")
    public ResponseEntity<ApiResponse<Page<PrincipalResponse>>> getAllPrincipals(
            Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getAllPrincipals(tenantId, pageable, actorId)));
    }

    @GetMapping("/principals/{id}")
    @Operation(summary = "Get a principal's full record (real name, medical notes, threats)")
    public ResponseEntity<ApiResponse<PrincipalResponse>> getPrincipal(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getPrincipal(tenantId, id, actorId)));
    }

    @PostMapping("/principals")
    @Operation(
            summary = "Register a new principal",
            description = "aliasCodename must be unique per tenant — it's used everywhere " +
                    "instead of the real name in team comms, dashboards, and notifications.")
    public ResponseEntity<ApiResponse<PrincipalResponse>> createPrincipal(
            @Valid @RequestBody CreatePrincipalRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cpService.createPrincipal(tenantId, req)));
    }

    @PutMapping("/principals/{id}")
    @Operation(summary = "Update a principal's record")
    public ResponseEntity<ApiResponse<PrincipalResponse>> updatePrincipal(
            @PathVariable UUID id, @Valid @RequestBody UpdatePrincipalRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.updatePrincipal(tenantId, id, req)));
    }

    @DeleteMapping("/principals/{id}")
    @Operation(summary = "Deactivate a principal (does not delete historical engagement records)")
    public ResponseEntity<ApiResponse<Void>> deactivatePrincipal(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        cpService.deactivatePrincipal(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Audit trail (Part 9.3) ─────────────────────────────────────────────────

    @GetMapping("/principals/{id}/audit")
    @Operation(
            summary = "Full audit trail for a principal",
            description = "Every CREATED, UPDATED, VIEWED action on this principal record, " +
                    "newest first. Part 9.3 compliance requirement — 'who accessed " +
                    "this record and when.'")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AuditEvent>>> getPrincipalAudit(
            @PathVariable UUID id, Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getPrincipalAudit(tenantId, id, pageable)));
    }

    @GetMapping("/principals/{id}/audit/views")
    @Operation(
            summary = "Who has viewed this principal's full record",
            description = "VIEWED events only — the access log that answers 'who has looked " +
                    "at this person's medical notes and threat intel.'")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AuditEvent>>> getPrincipalViewHistory(
            @PathVariable UUID id, Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getPrincipalViewHistory(tenantId, id, pageable)));
    }

    // ── Protection Details ─────────────────────────────────────────────────────

    @GetMapping("/details")
    @Operation(summary = "List active or planned protection details across all principals")
    public ResponseEntity<ApiResponse<Page<ProtectionDetailResponse>>> getActiveOrPlanned(
            Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getActiveOrPlannedDetails(tenantId, pageable)));
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "Get a single protection detail")
    public ResponseEntity<ApiResponse<ProtectionDetailResponse>> getDetail(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getDetail(tenantId, id)));
    }

    @GetMapping("/principals/{principalId}/details")
    @Operation(summary = "All engagements for a principal — full history")
    public ResponseEntity<ApiResponse<List<ProtectionDetailResponse>>> getDetailsForPrincipal(
            @PathVariable UUID principalId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getDetailsForPrincipal(tenantId, principalId)));
    }

    @PostMapping("/details")
    @Operation(
            summary = "Create a new protection detail (engagement)",
            description = "detailType: STATIC | MOBILE | EVENT | TRAVEL. Starts in PLANNED status.")
    public ResponseEntity<ApiResponse<ProtectionDetailResponse>> createDetail(
            @Valid @RequestBody CreateProtectionDetailRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cpService.createDetail(tenantId, req)));
    }

    @PostMapping("/details/{id}/activate")
    @Operation(summary = "Activate a PLANNED detail")
    public ResponseEntity<ApiResponse<ProtectionDetailResponse>> activateDetail(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.activateDetail(tenantId, id)));
    }

    @PostMapping("/details/{id}/complete")
    @Operation(summary = "Mark an ACTIVE detail as completed")
    public ResponseEntity<ApiResponse<ProtectionDetailResponse>> completeDetail(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.completeDetail(tenantId, id)));
    }

    @PostMapping("/details/{id}/cancel")
    @Operation(summary = "Cancel a detail before completion")
    public ResponseEntity<ApiResponse<ProtectionDetailResponse>> cancelDetail(
            @PathVariable UUID id, @Valid @RequestBody CancelDetailRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.cancelDetail(tenantId, id, req)));
    }

    // ── Team Assignments ───────────────────────────────────────────────────────

    @GetMapping("/details/{id}/team")
    @Operation(summary = "Get the current team roster for a detail")
    public ResponseEntity<ApiResponse<List<DetailAssignmentResponse>>> getTeamRoster(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getTeamRoster(tenantId, id)));
    }

    @PostMapping("/details/{id}/team")
    @Operation(
            summary = "Assign a guard to a role on this detail",
            description = "role: TEAM_LEADER | DRIVER | CPO | ADVANCE | COUNTER_SURVEILLANCE. " +
                    "A guard cannot hold two open-ended roles on the same detail.")
    public ResponseEntity<ApiResponse<DetailAssignmentResponse>> assignToDetail(
            @PathVariable UUID id, @Valid @RequestBody AssignToDetailRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                cpService.assignToDetail(tenantId, id, req)));
    }

    @DeleteMapping("/team/{assignmentId}")
    @Operation(summary = "End a guard's role on a detail")
    public ResponseEntity<ApiResponse<Void>> endAssignment(@PathVariable UUID assignmentId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        cpService.endAssignment(tenantId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Itinerary ──────────────────────────────────────────────────────────────

    @GetMapping("/details/{id}/itinerary")
    @Operation(summary = "Full itinerary for a detail, in sequence order")
    public ResponseEntity<ApiResponse<List<ItineraryStopResponse>>> getItinerary(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getItinerary(tenantId, id)));
    }

    @GetMapping("/details/{id}/itinerary/current")
    @Operation(summary = "The current/next stop — first stop not yet departed",
            description = "Used for the live status view (\"Stop 3 of 6 — Restaurant, en route\"). " +
                    "Returns null if all stops are completed or none exist yet.")
    public ResponseEntity<ApiResponse<ItineraryStopResponse>> getCurrentStop(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getCurrentStop(tenantId, id)));
    }

    @PostMapping("/details/{id}/itinerary")
    @Operation(summary = "Add a stop to a detail's itinerary",
            description = "Sequence is assigned automatically (appended to the end).")
    public ResponseEntity<ApiResponse<ItineraryStopResponse>> addStop(
            @PathVariable UUID id, @Valid @RequestBody AddItineraryStopRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cpService.addStop(tenantId, id, req)));
    }

    @PostMapping("/itinerary/{stopId}/arrive")
    @Operation(summary = "Record arrival at an itinerary stop")
    public ResponseEntity<ApiResponse<ItineraryStopResponse>> recordArrival(
            @PathVariable UUID stopId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.recordStopArrival(tenantId, stopId)));
    }

    @PostMapping("/itinerary/{stopId}/depart")
    @Operation(summary = "Record departure from an itinerary stop")
    public ResponseEntity<ApiResponse<ItineraryStopResponse>> recordDeparture(
            @PathVariable UUID stopId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.recordStopDeparture(tenantId, stopId)));
    }

    // ── Advance Surveys ────────────────────────────────────────────────────────

    @GetMapping("/itinerary/{stopId}/surveys")
    @Operation(summary = "All advance surveys conducted for an itinerary stop")
    public ResponseEntity<ApiResponse<List<AdvanceSurveyResponse>>> getSurveysForStop(
            @PathVariable UUID stopId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getSurveysForStop(tenantId, stopId)));
    }

    @PostMapping("/itinerary/{stopId}/surveys")
    @Operation(
            summary = "Conduct an advance survey at an itinerary stop",
            description = """
            Recon check before the principal arrives. Multiple surveys per
            stop are allowed (one per surveying guard) — a high-threat-level
            detail may want independent confirmation from two guards before
            clearing the location.
            """)
    public ResponseEntity<ApiResponse<AdvanceSurveyResponse>> conductSurvey(
            @PathVariable UUID stopId, @Valid @RequestBody ConductSurveyRequest req) {
        TenantId tenantId  = TenantContext.getTenantIdAsObject();
        UUID     guardId   = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                cpService.conductSurvey(tenantId, stopId, guardId, req)));
    }

    @GetMapping("/itinerary/{stopId}/cleared")
    @Operation(summary = "Whether at least one ALL_CLEAR survey exists for this stop")
    public ResponseEntity<ApiResponse<Boolean>> isStopCleared(@PathVariable UUID stopId) {
        return ResponseEntity.ok(ApiResponse.success(cpService.isStopCleared(stopId)));
    }

    // ── Protection Vehicles ────────────────────────────────────────────────────

    @GetMapping("/vehicles")
    @Operation(summary = "List all active (non-decommissioned) protection vehicles")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAllVehicles(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getAllVehicles(tenantId, pageable)));
    }

    @PostMapping("/vehicles")
    @Operation(summary = "Register a new protection vehicle",
            description = "vehicleType: PRINCIPAL_CAR | LEAD_CAR | FOLLOW_CAR")
    public ResponseEntity<ApiResponse<VehicleResponse>> registerVehicle(
            @Valid @RequestBody RegisterVehicleRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cpService.registerVehicle(tenantId, req)));
    }

    @PostMapping("/vehicles/{id}/driver")
    @Operation(summary = "Assign a driver to a vehicle")
    public ResponseEntity<ApiResponse<VehicleResponse>> assignDriver(
            @PathVariable UUID id, @Valid @RequestBody AssignDriverRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.assignDriver(tenantId, id, req)));
    }

    @DeleteMapping("/vehicles/{id}/driver")
    @Operation(summary = "Release a vehicle's driver (returns vehicle to AVAILABLE)")
    public ResponseEntity<ApiResponse<VehicleResponse>> releaseDriver(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.releaseDriver(tenantId, id)));
    }

    @PostMapping("/vehicles/{id}/service")
    @Operation(summary = "Send a vehicle for maintenance")
    public ResponseEntity<ApiResponse<VehicleResponse>> sendForService(
            @PathVariable UUID id, @Valid @RequestBody ServiceVehicleRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.sendForService(tenantId, id, req)));
    }

    @PostMapping("/vehicles/{id}/return-from-service")
    @Operation(summary = "Return a vehicle from maintenance to AVAILABLE")
    public ResponseEntity<ApiResponse<VehicleResponse>> returnFromService(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.returnFromService(tenantId, id)));
    }

    @PostMapping("/vehicles/{id}/decommission")
    @Operation(summary = "Permanently retire a vehicle")
    public ResponseEntity<ApiResponse<VehicleResponse>> decommissionVehicle(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.decommissionVehicle(tenantId, id)));
    }
}
