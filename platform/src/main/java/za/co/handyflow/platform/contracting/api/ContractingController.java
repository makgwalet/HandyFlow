package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.contracting.application.internal.ClientIpResolver;
import za.co.handyflow.platform.contracting.application.internal.ContractingService;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracting", description = "Contract lifecycle, templates and OTP signing")
public class ContractingController {

    private final ContractingService contractingService;
    private final ClientIpResolver clientIps;

    // ── Templates ─────────────────────────────────────────────────────────────

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Edit a template that is still DRAFT or INTERNAL_REVIEW. Published wording is changed through a new version")
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Template updated",
                contractingService.updateTemplate(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/templates/{id}/archive")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Archive a custom template so it no longer appears in the picker")
    public ResponseEntity<ApiResponse<Void>> archiveTemplate(@PathVariable UUID id) {
        contractingService.archiveTemplate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Template archived", null));
    }

    @PostMapping("/templates/{id}/submit-internal-review")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Template lifecycle: DRAFT to INTERNAL_REVIEW")
    public ResponseEntity<ApiResponse<TemplateResponse>> submitTemplateInternalReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template submitted for internal review",
                contractingService.submitTemplateForInternalReview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/templates/{id}/submit-legal-review")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Template lifecycle: INTERNAL_REVIEW to LEGAL_REVIEW")
    public ResponseEntity<ApiResponse<TemplateResponse>> submitTemplateLegalReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template submitted for legal review",
                contractingService.submitTemplateForLegalReview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/templates/{id}/legal-reviews")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Record a South African legal practitioner's review of this template version (APPROVED, CONDITIONAL or REJECTED)")
    public ResponseEntity<ApiResponse<LegalReviewResponse>> recordTemplateLegalReview(
            @PathVariable UUID id, @Valid @RequestBody RecordLegalReviewRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Legal review recorded",
                contractingService.recordTemplateLegalReview(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/templates/{id}/legal-reviews")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Legal review history of a template, newest first")
    public ResponseEntity<ApiResponse<List<LegalReviewResponse>>> getTemplateLegalReviews(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Legal reviews",
                contractingService.getTemplateLegalReviews(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping(value = "/templates/{id}/legal-reviews/{reviewId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Attach the reviewer's signed-off review to a recorded legal review. A second file is stored as a new version")
    public ResponseEntity<ApiResponse<LegalReviewResponse>> uploadLegalReviewEvidence(
            @PathVariable UUID id, @PathVariable UUID reviewId, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Evidence stored", contractingService.uploadLegalReviewEvidence(
                TenantContext.getTenantIdAsObject(), id, reviewId,
                new DocumentsFacade.FileUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes()))));
    }

    @GetMapping("/templates/{id}/legal-reviews/{reviewId}/evidence")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Download the evidence file of a legal review (the latest version, or the numbered one). The download is audited")
    public ResponseEntity<byte[]> downloadLegalReviewEvidence(@PathVariable UUID id, @PathVariable UUID reviewId,
                                                              @RequestParam(required = false) Integer version) {
        DocumentsFacade.Download file = contractingService.downloadLegalReviewEvidence(TenantContext.getTenantIdAsObject(), id, reviewId, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/templates/{id}/legal-reviews/{reviewId}/withdraw")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Withdraw a recorded legal review. A published template then stops being offered for new contracts")
    public ResponseEntity<ApiResponse<LegalReviewResponse>> withdrawTemplateLegalReview(
            @PathVariable UUID id, @PathVariable UUID reviewId, @Valid @RequestBody WithdrawLegalReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Legal review withdrawn",
                contractingService.withdrawTemplateLegalReview(TenantContext.getTenantIdAsObject(), id, reviewId, req.reason())));
    }

    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Publish an APPROVED template. Requires a current legal approval; supersedes the previous version")
    public ResponseEntity<ApiResponse<TemplateResponse>> publishTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template published",
                contractingService.publishTemplate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/templates/{id}/new-version")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Start a new DRAFT version of a published template")
    public ResponseEntity<ApiResponse<TemplateResponse>> newTemplateVersion(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(ApiResponse.success("New template version created",
                contractingService.newTemplateVersion(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Dashboard figures: counts by status and type, active and pending value, expiring soon, awaiting signature")
    public ResponseEntity<ApiResponse<ContractDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard",
                contractingService.getDashboard(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "List contract templates — seeds the SA system templates on first call")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getTemplates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE')")
    @Operation(summary = "Create a custom contract template with {{variable}} placeholders")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Template created",
                contractingService.createTemplate(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "List contracts — lightweight summary response (no body HTML)")
    public ResponseEntity<ApiResponse<Page<ContractSummaryResponse>>> getContracts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getContracts(TenantContext.getTenantIdAsObject(),
                        status, type, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Get full contract detail including body HTML, parties and signing status")
    public ResponseEntity<ApiResponse<ContractResponse>> getContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getContract(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Create contract — from template (resolves variables) or blank body")
    public ResponseEntity<ApiResponse<ContractResponse>> createContract(
            @Valid @RequestBody CreateContractRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Contract created",
                contractingService.createContract(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    // NEW: was no way to edit a contract at all after creation — a
    // contract created with blanks left in it (see ContractsTab.tsx's
    // "Leave blank to fill in later" flow) had no path back to actually
    // finish it short of deleting and recreating it, and sendForSigning()'s
    // unresolved-variable check was consequently a dead end. Only
    // DRAFT/UNDER_REVIEW contracts can be edited — enforced by
    // Contract.assertEditable(), which every mutator this hits already
    // goes through.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Edit a DRAFT or UNDER_REVIEW contract — fill in remaining {{variables}} and/or update dates, value, notes, auto-renew")
    public ResponseEntity<ApiResponse<ContractResponse>> updateContract(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contract updated",
                contractingService.updateContract(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Submit contract for internal review (DRAFT → UNDER_REVIEW)")
    public ResponseEntity<ApiResponse<ContractResponse>> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted for review",
                contractingService.submitForReview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/send-for-signing")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Send to parties for signing — validates no unresolved {{variables}}, " +
            "locks body hash, emails signing links, sends SMS notifications")
    public ResponseEntity<ApiResponse<ContractResponse>> sendForSigning(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Sent for signing",
                contractingService.sendForSigning(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_ADMIN','CONTRACTS_MANAGE')")
    @Operation(summary = "Withdraw a contract that was sent for signing: revokes every signing link, keeps all evidence")
    public ResponseEntity<ApiResponse<ContractResponse>> withdraw(
            @PathVariable UUID id,
            @RequestBody WithdrawContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Signing request withdrawn",
                contractingService.withdraw(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @GetMapping("/{id}/withdrawal")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Who withdrew the contract, when and why (404 if it was not withdrawn)")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> withdrawal(@PathVariable UUID id) {
        var tenant = TenantContext.getTenantIdAsObject();
        return contractingService.getWithdrawal(tenant, id)
                .map(w -> ResponseEntity.ok(ApiResponse.success("Withdrawal", new WithdrawalResponse(
                        w.getWithdrawnBy(), w.getWithdrawnAt(), w.getReason(), w.getLinksRevoked(), w.getOtpChallengesCleared(),
                        w.getPartiesSigned(), w.getPartiesTotal(), contractingService.getRevisionId(tenant, id).orElse(null)))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/new-revision")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Make a new draft from a withdrawn contract (the withdrawn record is not changed)")
    public ResponseEntity<ApiResponse<ContractResponse>> newRevision(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("New draft created",
                contractingService.reviseWithdrawn(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_ADMIN')")
    @Operation(summary = "Terminate a SIGNED contract with a reason")
    public ResponseEntity<ApiResponse<ContractResponse>> terminate(
            @PathVariable UUID id,
            @RequestBody TerminateContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                contractingService.terminate(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    // ── Parties ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/parties")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Add a signing party to a contract")
    public ResponseEntity<ApiResponse<PartyResponse>> addParty(
            @PathVariable UUID id,
            @Valid @RequestBody AddPartyRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Party added",
                contractingService.addParty(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/parties/{partyId}/request-otp")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_SIGN','CONTRACTS_MANAGE')")
    @Operation(summary = "Send OTP to party's phone — enforces signing order, rate-limited 3/10min")
    public ResponseEntity<ApiResponse<PartyResponse>> requestOtp(
            @PathVariable UUID id, @PathVariable UUID partyId) {
        return ResponseEntity.ok(ApiResponse.success("OTP sent",
                contractingService.requestOtp(TenantContext.getTenantIdAsObject(), id, partyId)));
    }

    @PostMapping("/{id}/parties/{partyId}/resend")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Resend signing link to a party — revokes old token, issues new 72h link")
    public ResponseEntity<ApiResponse<PartyResponse>> resendSigningLink(
            @PathVariable UUID id, @PathVariable UUID partyId) {
        return ResponseEntity.ok(ApiResponse.success("Signing link resent",
                contractingService.resendSigningLink(TenantContext.getTenantIdAsObject(), id, partyId)));
    }

    // NEW: was called by ContractsTab.tsx (staff-facing UI) but never existed
    // on the backend at all — ContractingService.signContract() already had
    // the complete, correct logic (OTP verification, signature recording,
    // all-parties-signed check, notifications) sitting unused with no
    // endpoint wired to it. For staff-witnessed in-person signing: the party
    // is physically present, staff requests an OTP on their behalf (existing
    // request-otp endpoint above), the party reads it off their own phone,
    // and staff enters it here alongside a captured signature — the same
    // OTP-verified evidentiary trail as the remote flow, just over a
    // different channel. witnessedByUserId (the authenticated staff member)
    // is recorded on the signature for audit purposes — see
    // ContractSignature.witnessedByUserId's Javadoc.
    @PostMapping("/{id}/parties/{partyId}/sign")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_SIGN','CONTRACTS_MANAGE')")
    @Operation(summary = "Record an in-person, staff-witnessed signature — same OTP verification as the remote flow")
    public ResponseEntity<ApiResponse<ContractResponse>> signInPerson(
            @PathVariable UUID id,
            @PathVariable UUID partyId,
            @Valid @RequestBody SignContractRequest req,
            HttpServletRequest http) {
        String ip = clientIps.address(http);
        String ua = http.getHeader("User-Agent");
        return ResponseEntity.ok(ApiResponse.success("Signature recorded",
                contractingService.signContract(TenantContext.getTenantIdAsObject(), id, partyId,
                        req, ip, ua, TenantContext.getCurrentUserId())));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    // NEW: was called by ContractsTab.tsx but never existed on the backend.
    // ContractComment.partyId was already designed to be nullable
    // ("null = posted by internal HandyFlow user") — the entity/schema was
    // ready, just had no service method or endpoint using that pathway.
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_MANAGE')")
    @Operation(summary = "Post a comment or amendment request as internal staff — visible to all parties and owner")
    public ResponseEntity<ApiResponse<CommentView>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddCommentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Comment posted",
                contractingService.addCommentAsStaff(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId(), req)));
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')")
    @Operation(summary = "Download signed or terminated contract as PDF with audit trail")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = contractingService.generatePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + id + ".pdf\"")
                .body(pdf);
    }
}