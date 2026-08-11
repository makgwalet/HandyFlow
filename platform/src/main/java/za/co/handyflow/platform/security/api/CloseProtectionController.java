// security/api/CloseProtectionController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.CloseProtectionService;
import za.co.handyflow.platform.security.application.internal.CpCompliancePdfService;
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.security.domain.model.AuditEvent;
import za.co.handyflow.platform.security.domain.model.CpEvidence;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * CloseProtectionController — VIP/Close Protection module core (Part 9).
 *
 * CHANGE: added GET /principals/{id}/vetting/pdf -- exportable Part 9.6
 * vetting compliance record (audit gap: "no export path for a compliance
 * officer needing a paper record"). Sits under the same class-level
 * VIP_DETAIL_ACCESS gate as everything else here — no separate permission
 * needed, since generating the PDF requires the same access as viewing the
 * underlying data would.
 *
 * All other endpoints/comments unchanged from the V211 version.
 */
@Tag(name = "Security - Close Protection")
@RestController
@RequestMapping("/api/v1/security/cp")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VIP_DETAIL_ACCESS')")
public class CloseProtectionController {

    private final CloseProtectionService  cpService;
    private final CpCompliancePdfService  cpCompliancePdfService;

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

    // ── Vetting compliance PDF ─────────────────────────────────────────────────

    @GetMapping("/principals/{id}/vetting/pdf")
    @Operation(
            summary = "Vetting compliance record PDF",
            description = "Part 9.6 compliance record for a compliance officer needing a paper " +
                    "trail — alias, threat level, vetting status, full check history, and any " +
                    "declined-engagement notice. Deliberately excludes medical notes, known " +
                    "threats, and any declined-engagement sensitive detail -- see " +
                    "CpCompliancePdfService for why.")
    public ResponseEntity<byte[]> getVettingCompliancePdf(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = cpCompliancePdfService.vettingCompliancePdf(tenantId, id);
        return pdfResponse(pdf, "vetting-compliance-" + id.toString().substring(0, 8) + ".pdf");
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

    @PostMapping("/details/{id}/clone")
    @Operation(
            summary = "Spin up a new detail from a previous engagement for the same principal",
            description = """
            The repeat-client case -- e.g. a VC who needs the same protection
            shape on every campus visit. Copies principalId/detailType/
            billingRate/notes from the source detail. Team roster and
            itinerary are re-created (not blindly copied): each team member
            is re-validated through the same hard gates a manual assignment
            would hit (guard still schedulable, CP vetting tier still
            sufficient for the principal's threat level) -- anyone who no
            longer qualifies is skipped and reported back in the response
            rather than silently dropped. Itinerary stop timing is shifted
            by the same offset as the new detail's start time relative to
            the source's, so "Day 1, 9am arrival" reproduces correctly on
            the new date.
            """)
    public ResponseEntity<ApiResponse<CloneDetailResult>> cloneDetail(
            @PathVariable UUID id, @Valid @RequestBody CloneDetailRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                cpService.cloneDetail(tenantId, id, actorId, req)));
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

    // ── Arms <-> CP linkage (V211) ─────────────────────────────────────────────

    @PostMapping("/details/{id}/team/{assignmentId}/firearms/{armouryId}/issue")
    @Operation(
            summary = "Issue a firearm to a guard as part of this detail's team roster",
            description = """
            Thin wrapper around the existing witnessed Armoury issue workflow
            (ArmouryService.issue) -- all hard blocks there (license expiry,
            firearm availability, receiving guard's competency, witness
            validity, mandatory two-person witness) apply unchanged. Adds one
            check on top: the assignment must actually belong to this detail
            and to the guard named in the request body, so a firearm can't be
            issued "for" a detail to someone not actually on its roster.
            The resulting ArmouryLog entry is linked back to this detail --
            see GET /details/{id}/armoury.
            """)
    public ResponseEntity<ApiResponse<ArmouryResponse>> issueFirearmForDetail(
            @PathVariable UUID id, @PathVariable UUID assignmentId, @PathVariable UUID armouryId,
            @Valid @RequestBody IssueFirearmRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.issueFirearmForDetail(tenantId, id, assignmentId, armouryId, req)));
    }

    @GetMapping("/details/{id}/armoury")
    @Operation(
            summary = "Firearm issue/return history linked to this detail",
            description = "Every ArmouryLog entry created via the issue-for-detail endpoint " +
                    "above -- \"which firearms are or were out on this engagement.\"")
    public ResponseEntity<ApiResponse<List<ArmouryLog>>> getArmouryForDetail(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cpService.getArmouryForDetail(tenantId, id)));
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

    // ── Evidence (V211) ────────────────────────────────────────────────────────

    @PostMapping("/principals/{id}/evidence")
    @Operation(summary = "Upload evidence attached to a principal",
            description = "category: ID_DOCUMENT | ENGAGEMENT_LETTER | THREAT_INTEL | MEDICAL | OTHER")
    public ResponseEntity<ApiResponse<EvidenceResponse>> uploadPrincipalEvidence(
            @PathVariable UUID id, @Valid @RequestBody UploadEvidenceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                cpService.uploadEvidence(tenantId, CpEvidence.EntityType.PRINCIPAL, id, req, actorId)));
    }

    @GetMapping("/principals/{id}/evidence")
    @Operation(summary = "List active evidence for a principal")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getPrincipalEvidence(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getEvidenceFor(tenantId, CpEvidence.EntityType.PRINCIPAL, id)));
    }

    @PostMapping("/details/{id}/evidence")
    @Operation(summary = "Upload evidence attached to a protection detail",
            description = "category: ID_DOCUMENT | ENGAGEMENT_LETTER | THREAT_INTEL | MEDICAL | OTHER")
    public ResponseEntity<ApiResponse<EvidenceResponse>> uploadDetailEvidence(
            @PathVariable UUID id, @Valid @RequestBody UploadEvidenceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                cpService.uploadEvidence(tenantId, CpEvidence.EntityType.PROTECTION_DETAIL, id, req, actorId)));
    }

    @GetMapping("/details/{id}/evidence")
    @Operation(summary = "List active evidence for a protection detail")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getDetailEvidence(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getEvidenceFor(tenantId, CpEvidence.EntityType.PROTECTION_DETAIL, id)));
    }

    @DeleteMapping("/evidence/{evidenceId}")
    @Operation(summary = "Soft-delete an evidence record",
            description = "Not a hard delete -- the record and who removed it (and why) survives, " +
                    "same posture as other compliance-sensitive records in this module.")
    public ResponseEntity<ApiResponse<Void>> deleteEvidence(
            @PathVariable UUID evidenceId, @Valid @RequestBody DeleteEvidenceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID     actorId  = TenantContext.getCurrentUserId();
        cpService.deleteEvidence(tenantId, evidenceId, actorId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}