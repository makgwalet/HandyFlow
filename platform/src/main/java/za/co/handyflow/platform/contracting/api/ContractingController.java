package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractingService;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracting", description = "Contract lifecycle, templates and OTP signing")
public class ContractingController {

    private final ContractingService contractingService;

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
    @Operation(summary = "List contract templates — seeds 5 SA system templates on first call")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getTemplates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Create a custom contract template with {{variable}} placeholders")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Template created",
                contractingService.createTemplate(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
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
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
    @Operation(summary = "Get full contract detail including body HTML, parties and signing status")
    public ResponseEntity<ApiResponse<ContractResponse>> getContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getContract(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Create contract — from template (resolves variables) or blank body")
    public ResponseEntity<ApiResponse<ContractResponse>> createContract(
            @Valid @RequestBody CreateContractRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Contract created",
                contractingService.createContract(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Submit contract for internal review (DRAFT → UNDER_REVIEW)")
    public ResponseEntity<ApiResponse<ContractResponse>> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted for review",
                contractingService.submitForReview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/send-for-signing")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Send to parties for signing — validates no unresolved {{variables}}, " +
            "locks body hash, emails signing links, sends SMS notifications")
    public ResponseEntity<ApiResponse<ContractResponse>> sendForSigning(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Sent for signing",
                contractingService.sendForSigning(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_ADMIN')")
    @Operation(summary = "Terminate a SIGNED contract with a reason")
    public ResponseEntity<ApiResponse<ContractResponse>> terminate(
            @PathVariable UUID id,
            @RequestBody TerminateContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                contractingService.terminate(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    // ── Parties ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/parties")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Add a signing party to a contract")
    public ResponseEntity<ApiResponse<PartyResponse>> addParty(
            @PathVariable UUID id,
            @Valid @RequestBody AddPartyRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Party added",
                contractingService.addParty(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/parties/{partyId}/request-otp")
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
    @Operation(summary = "Send OTP to party's phone — enforces signing order, rate-limited 3/10min")
    public ResponseEntity<ApiResponse<PartyResponse>> requestOtp(
            @PathVariable UUID id, @PathVariable UUID partyId) {
        return ResponseEntity.ok(ApiResponse.success("OTP sent",
                contractingService.requestOtp(TenantContext.getTenantIdAsObject(), id, partyId)));
    }

    @PostMapping("/{id}/parties/{partyId}/resend")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
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
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE')")
    @Operation(summary = "Record an in-person, staff-witnessed signature — same OTP verification as the remote flow")
    public ResponseEntity<ApiResponse<ContractResponse>> signInPerson(
            @PathVariable UUID id,
            @PathVariable UUID partyId,
            @Valid @RequestBody SignContractRequest req,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
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
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
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
    @PreAuthorize("hasAnyAuthority('USER_READ','USER_UPDATE')")
    @Operation(summary = "Download signed or terminated contract as PDF with audit trail")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = contractingService.generatePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + id + ".pdf\"")
                .body(pdf);
    }
}
