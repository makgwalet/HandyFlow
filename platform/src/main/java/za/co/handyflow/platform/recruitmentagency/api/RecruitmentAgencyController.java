package za.co.handyflow.platform.recruitmentagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.recruitmentagency.application.internal.RecruitmentAgencyService;
import za.co.handyflow.platform.recruitmentagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Foundation-layer endpoints only — agency profile and client
 * portfolio. FeatureGuard-gated the same as every other separately-
 * subscribable module in this platform.
 */
@RestController
@RequestMapping("/api/v1/recruitment-agency")
@RequiredArgsConstructor
@Tag(name = "Recruitment Agency", description = "Multi-client recruitment agency practice management")
public class RecruitmentAgencyController {

    private final RecruitmentAgencyService agencyService;
    private final FeatureGuard featureGuard;

    // ── Agency profile ───────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get the agency's own practice profile")
    public ResponseEntity<ApiResponse<AgencyProfileResponse>> getProfile() {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Create or update the agency's practice profile")
    public ResponseEntity<ApiResponse<AgencyProfileResponse>> upsertProfile(
            @Valid @RequestBody UpdateAgencyProfileRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                agencyService.upsertProfile(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List active agency clients")
    public ResponseEntity<ApiResponse<Page<AgencyClientResponse>>> getClients(
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<AgencyClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Onboard a new agency client")
    public ResponseEntity<ApiResponse<AgencyClientResponse>> createClient(
            @Valid @RequestBody CreateAgencyClientRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client onboarded",
                agencyService.createClient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<AgencyClientResponse>> updateClient(
            @PathVariable UUID id, @Valid @RequestBody CreateAgencyClientRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                agencyService.updateClient(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<AgencyClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                agencyService.deactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<AgencyClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                agencyService.reactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        agencyService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }


    @PostMapping("/requisitions")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<RequisitionResponse>> createRequisition(
            @Valid @RequestBody CreateRequisitionRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requisition created",
                agencyService.createRequisition(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/clients/{clientId}/requisitions")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<RequisitionResponse>>> getRequisitionsForClient(
            @PathVariable UUID clientId) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getRequisitionsForClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/requisitions/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<RequisitionResponse>> getRequisition(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getRequisition(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/requisitions/{id}/cancel")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<RequisitionResponse>> cancelRequisition(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Requisition cancelled",
                agencyService.cancelRequisition(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Candidates ────────────────────────────────────────────────────────

    @PostMapping("/candidates")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<CandidateResponse>> createCandidate(
            @Valid @RequestBody CreateCandidateRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate added",
                agencyService.createCandidate(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<CandidateResponse>>> searchCandidates(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.searchCandidates(TenantContext.getTenantIdAsObject(), search, pageable)));
    }

    @PostMapping(value = "/candidates/{id}/cv", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<CandidateResponse>> uploadCv(
            @PathVariable UUID id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("CV uploaded",
                agencyService.uploadCv(TenantContext.getTenantIdAsObject(), id, file)));
    }

    @GetMapping("/candidates/{id}/cv")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<byte[]> downloadCv(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        byte[] cv = agencyService.downloadCv(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"cv.pdf\"")
                .body(cv);
    }

    // ── Placements / pipeline ────────────────────────────────────────────

    @PostMapping("/requisitions/{requisitionId}/submit-candidate")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<PlacementResponse>> submitCandidate(
            @PathVariable UUID requisitionId, @Valid @RequestBody SubmitCandidateRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate submitted",
                agencyService.submitCandidate(TenantContext.getTenantIdAsObject(), requisitionId, req)));
    }

    @GetMapping("/requisitions/{requisitionId}/placements")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<PlacementResponse>>> getPlacements(@PathVariable UUID requisitionId) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getPlacementsForRequisition(TenantContext.getTenantIdAsObject(), requisitionId)));
    }

    @PostMapping("/placements/{id}/advance-stage")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<PlacementResponse>> advanceStage(
            @PathVariable UUID id, @Valid @RequestBody AdvanceStageRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Stage updated",
                agencyService.advanceStage(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/placements/{id}/mark-placed")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Confirm the placement — computes the fee, marks the requisition filled, withdraws other candidates")
    public ResponseEntity<ApiResponse<PlacementResponse>> markPlaced(
            @PathVariable UUID id, @Valid @RequestBody MarkPlacedRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Placement confirmed",
                agencyService.markPlaced(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/placements/{id}/stage-history")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<StageHistoryResponse>>> getStageHistory(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getStageHistory(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/placements/{id}/invoice")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Generate an invoice for a confirmed (PLACED) placement")
    public ResponseEntity<ApiResponse<AgencyInvoiceResponse>> generateInvoice(
            @PathVariable UUID id, @Valid @RequestBody CreateAgencyInvoiceRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated",
                agencyService.generateInvoice(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/clients/{clientId}/invoices")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<AgencyInvoiceResponse>>> getInvoices(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getInvoices(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<AgencyInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                agencyService.sendInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<AgencyInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordAgencyPaymentRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded",
                agencyService.recordPayment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/placements/{id}/fail-guarantee")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Report that a placed candidate left within the guarantee period")
    public ResponseEntity<ApiResponse<PlacementResponse>> failGuarantee(
            @PathVariable UUID id, @Valid @RequestBody FailGuaranteeRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Guarantee failure recorded",
                agencyService.failGuarantee(TenantContext.getTenantIdAsObject(), id, req.reason(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Recruitment Agency Client Portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invitePortalUser(
            @PathVariable UUID id, @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                agencyService.invitePortalUser(TenantContext.getTenantIdAsObject(), id,
                        req.email(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAuthority('USER_READ')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Recruitment Agency Client Portal")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> getPortalAccessGrants(@PathVariable UUID id) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/portal-invites/{grantId}/revoke")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Recruitment Agency Client Portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revokePortalAccess(
            @PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("recruitmentagency");
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                agencyService.revokePortalAccess(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
    }
}