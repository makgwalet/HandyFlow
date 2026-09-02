package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalpractice.application.internal.LpClientService;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustTransactionService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * The firm's client portfolio, plus every client-level sub-resource:
 * trust transactions (delegating to {@code LpTrustTransactionService}),
 * portal access grants (delegating to {@code LpPortalService}), and
 * client-level documents via {@code EvidenceFacade} — mirroring how
 * {@code CollAgencyDebtorAccountController} nests its own sub-resources
 * rather than standing up separate top-level controllers for each.
 * <p>
 * Trust money movement is the module's compliance-sensitive action:
 * TRANSFER_TO_BUSINESS/DISBURSEMENT_PAYMENT/REFUND are ADMIN-gated
 * (matching {@code collectionsagency}'s own precedent that processing a
 * remittance is ADMIN-only), while a plain RECEIPT — money simply
 * arriving in trust, nothing yet moving out — stays MANAGE-gated. Hard-
 * deleting a client is likewise ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/clients")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Clients", description = "Client portfolio, trust ledger, portal access, documents")
public class LpClientController {

    private static final String SOURCE_MODULE = "legalpractice";
    private static final String ENTITY_TYPE = "LpClient";

    private final LpClientService clientService;
    private final LpTrustTransactionService trustService;
    private final LpPortalService portalService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    // ── Client CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpClientResponse>>> getClients(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.listClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpClientResponse>> createClient(@Valid @RequestBody CreateLpClientRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client created",
                clientService.createClient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpClientResponse>> updateClient(
            @PathVariable UUID id, @Valid @RequestBody UpdateLpClientRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                clientService.updateClient(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                clientService.deactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                clientService.reactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Permanently delete a client record — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        clientService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Trust ledger sub-resource ────────────────────────────────────────────

    @GetMapping("/{id}/trust-transactions")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpTrustTransactionResponse>>> getTrustTransactions(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                trustService.listByClient(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/{id}/trust-transactions/receipt")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Record money deposited into trust by/for this client")
    public ResponseEntity<ApiResponse<LpTrustTransactionResponse>> recordReceipt(
            @PathVariable UUID id, @Valid @RequestBody RecordTrustReceiptRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Trust receipt recorded",
                trustService.recordReceipt(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/trust-transactions/transfer-to-business")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Draw the firm's own earned fees out of trust against a real invoice — ADMIN only")
    public ResponseEntity<ApiResponse<LpTrustTransactionResponse>> transferToBusiness(
            @PathVariable UUID id, @Valid @RequestBody TransferToBusinessRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Trust funds transferred to business",
                trustService.transferToBusiness(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/trust-transactions/pay-disbursement")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Pay a third party directly from trust on the client's behalf — ADMIN only")
    public ResponseEntity<ApiResponse<LpTrustTransactionResponse>> payDisbursement(
            @PathVariable UUID id, @Valid @RequestBody PayDisbursementFromTrustRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Disbursement paid from trust",
                trustService.payDisbursement(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/trust-transactions/refund")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Refund trust money directly to the client — ADMIN only")
    public ResponseEntity<ApiResponse<LpTrustTransactionResponse>> refund(
            @PathVariable UUID id, @Valid @RequestBody RefundTrustRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Trust refund recorded",
                trustService.refund(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    // ── Portal access grant sub-resource ─────────────────────────────────────

    @GetMapping("/{id}/portal-access")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpPortalAccessGrantResponse>>> getPortalAccess(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                portalService.listForClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/portal-access")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Invite a client contact to the portal")
    public ResponseEntity<ApiResponse<LpPortalAccessGrantResponse>> grantPortalAccess(
            @PathVariable UUID id, @Valid @RequestBody InviteClientToPortalRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Portal invite sent",
                portalService.inviteClientToPortal(TenantContext.getTenantIdAsObject(), id, req.inviteEmail(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/portal-access/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpPortalAccessGrantResponse>> revokePortalAccess(
            @PathVariable UUID id, @PathVariable UUID grantId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), grantId, TenantContext.getCurrentUserId())));
    }

    // ── Client-level documents (EvidenceFacade passthrough) ──────────────────

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Attach a document to this client — signed mandate, correspondence, etc.")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(evidenceFacade.attach(
                TenantContext.getTenantIdAsObject(), file, evidenceType, SOURCE_MODULE, ENTITY_TYPE, id,
                null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("legalpractice");
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/{id}/evidence/{evidenceId}/detach")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> detachEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("legalpractice");
        evidenceFacade.detach(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
