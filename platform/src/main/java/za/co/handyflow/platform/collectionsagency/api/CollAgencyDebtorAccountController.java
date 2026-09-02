package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyClientService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyContactLogService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyDebtorAccountService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPaymentPlanService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPdfService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyProfileService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyContactLog;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPaymentPlan;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyProfile;
import za.co.handyflow.platform.collectionsagency.dto.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Debtor-account portfolio management, plus its two sub-resources —
 * contact log (mandatory NCA-compliant disclosure trail) and payment
 * plans — nested under the debtor account itself, same
 * "module wraps the generic endpoint as a sub-resource" convention
 * debtcollection's own controller already established for its case
 * sub-resources.
 */
@RestController
@RequestMapping("/api/v1/collections-agency")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Debtor Accounts", description = "Placed debtor accounts, contact log, payment plans")
public class CollAgencyDebtorAccountController {

    private static final String EVIDENCE_ENTITY_TYPE = "CollAgencyDebtorAccount";

    private final CollAgencyDebtorAccountService debtorAccountService;
    private final CollAgencyContactLogService contactLogService;
    private final CollAgencyPaymentPlanService paymentPlanService;
    private final CollAgencyClientService clientService;
    private final CollAgencyProfileService profileService;
    private final CollAgencyPdfService pdfService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    // ── Debtor accounts ──────────────────────────────────────────────────────

    @GetMapping("/clients/{clientId}/debtor-accounts")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<Page<DebtorAccountResponse>>> list(@PathVariable UUID clientId,
            @RequestParam(required = false) String status, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                debtorAccountService.listForClient(TenantContext.getTenantIdAsObject(), clientId, status, pageable)
                        .map(CollAgencyDebtorAccountController::toResponseStatic)));
    }

    @GetMapping("/clients/{clientId}/debtor-accounts/all")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Unpaginated portfolio listing — for the client recovery report/PDF export")
    public ResponseEntity<ApiResponse<List<DebtorAccountResponse>>> listAll(@PathVariable UUID clientId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                debtorAccountService.listAllForClient(TenantContext.getTenantIdAsObject(), clientId)
                        .stream().map(CollAgencyDebtorAccountController::toResponseStatic).toList()));
    }

    @GetMapping("/debtor-accounts/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<DebtorAccountResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                toResponseStatic(debtorAccountService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/debtor-accounts/{id}/assign")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<DebtorAccountResponse>> assign(@PathVariable UUID id,
                                                                      @Valid @RequestBody AssignCollectorRequest req) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Collector assigned", toResponseStatic(
                debtorAccountService.assign(TenantContext.getTenantIdAsObject(), id, req.collectorId()))));
    }

    @PostMapping("/debtor-accounts/{id}/status")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Advance the debtor account's workflow status")
    public ResponseEntity<ApiResponse<DebtorAccountResponse>> advanceStatus(@PathVariable UUID id,
                                                                             @Valid @RequestBody AdvanceStatusRequest req) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Status updated", toResponseStatic(
                debtorAccountService.advanceStatus(TenantContext.getTenantIdAsObject(), id, req.newStatus()))));
    }

    @DeleteMapping("/debtor-accounts/{id}")
    @PreAuthorize("hasAuthority('COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        debtorAccountService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Debtor account deleted", null));
    }

    // ── Contact log sub-resource ─────────────────────────────────────────────

    @GetMapping("/debtor-accounts/{id}/contacts")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<ContactLogResponse>>> listContacts(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                contactLogService.listForDebtorAccount(TenantContext.getTenantIdAsObject(), id)
                        .stream().map(this::toContactResponse).toList()));
    }

    @PostMapping("/debtor-accounts/{id}/contacts")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Record a contact attempt — all three NCA disclosures are mandatory on every contact")
    public ResponseEntity<ApiResponse<ContactLogResponse>> recordContact(@PathVariable UUID id,
                                                                          @Valid @RequestBody RecordContactRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyContactLog log = contactLogService.record(TenantContext.getTenantIdAsObject(), id,
                req.contactDate(), req.contactMethod(), req.outcome(), req.disclosedThirdPartyCollector(),
                req.disclosedOriginalCreditor(), req.disclosedDebtorRights(), req.notes(),
                req.promisedPaymentDate(), req.promisedPaymentAmount(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Contact recorded", toContactResponse(log)));
    }

    // ── Payment plan sub-resource ────────────────────────────────────────────

    @GetMapping("/debtor-accounts/{id}/payment-plans")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentPlanResponse>>> listPaymentPlans(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                paymentPlanService.listForDebtorAccount(TenantContext.getTenantIdAsObject(), id)
                        .stream().map(this::toPlanResponse).toList()));
    }

    @PostMapping("/debtor-accounts/{id}/payment-plans")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Propose a payment plan — also advances the debtor account to PAYMENT_PLAN_ACTIVE")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> proposePaymentPlan(@PathVariable UUID id,
                                                                                @Valid @RequestBody ProposePaymentPlanRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyPaymentPlan plan = paymentPlanService.propose(TenantContext.getTenantIdAsObject(), id,
                req.totalAgreedAmount(), req.installmentAmount(), req.frequency(), req.startDate(),
                req.numberOfInstallments(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment plan proposed", toPlanResponse(plan)));
    }

    @PostMapping("/payment-plans/{planId}/installment-paid")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> markInstallmentPaid(@PathVariable UUID planId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Installment recorded",
                toPlanResponse(paymentPlanService.markInstallmentPaid(TenantContext.getTenantIdAsObject(), planId))));
    }

    @PostMapping("/payment-plans/{planId}/default")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> markDefaulted(@PathVariable UUID planId,
                                                                           @RequestBody(required = false) ReasonRequest req) {
        featureGuard.requireModule("collectionsagency");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success("Payment plan marked defaulted",
                toPlanResponse(paymentPlanService.markDefaulted(TenantContext.getTenantIdAsObject(), planId, reason))));
    }

    @PostMapping("/payment-plans/{planId}/cancel")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> cancelPlan(@PathVariable UUID planId,
                                                                        @RequestBody(required = false) ReasonRequest req) {
        featureGuard.requireModule("collectionsagency");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success("Payment plan cancelled",
                toPlanResponse(paymentPlanService.cancel(TenantContext.getTenantIdAsObject(), planId, reason))));
    }

    // ── PDF documents ────────────────────────────────────────────────────────

    @GetMapping(value = "/clients/{clientId}/portfolio-statement/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Export the client's portfolio and recovery statement as a PDF")
    public ResponseEntity<byte[]> exportPortfolioStatement(@PathVariable UUID clientId) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        CollAgencyClient client = clientService.get(tenantId, clientId);
        CollAgencyProfile profile = profileService.get(tenantId);
        List<CollAgencyDebtorAccount> accounts = debtorAccountService.listAllForClient(tenantId, clientId);
        byte[] pdf = pdfService.generatePortfolioStatement(profile != null ? profile.getAgencyName() : null,
                client, accounts);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"portfolio-statement-" + client.getTradingName() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping(value = "/debtor-accounts/{id}/demand-letter/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Generate a formal demand letter for this debtor account — includes the mandatory NCA third-party-collector disclosures")
    public ResponseEntity<byte[]> exportDemandLetter(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        CollAgencyDebtorAccount account = debtorAccountService.get(tenantId, id);
        CollAgencyProfile profile = profileService.get(tenantId);
        byte[] pdf = pdfService.generateDemandLetter(profile != null ? profile.getAgencyName() : null,
                clientService.get(tenantId, account.getClientId()), account);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"demand-letter-" + nullSafe(account.getAccountReference()) + ".pdf\"")
                .body(pdf);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "account";
    }

    // ── Evidence (proof of debt, acknowledgment of debt, correspondence, ...) ──

    @PostMapping(value = "/debtor-accounts/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Attach a document (proof of debt, signed AOD, correspondence, ...) to this debtor account")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        debtorAccountService.get(tenantId, id); // 404s if the account doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "collectionsagency",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/debtor-accounts/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        debtorAccountService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, "collectionsagency", EVIDENCE_ENTITY_TYPE, id)));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /** static so CollAgencyPlacementController's "accounts in this batch" endpoint can reuse it without duplicating the mapping. */
    static DebtorAccountResponse toResponseStatic(CollAgencyDebtorAccount a) {
        return new DebtorAccountResponse(a.getId(), a.getClientId(), a.getPlacementBatchId(),
                a.getAccountReference(), a.getDebtorName(), a.getDebtorIdNumber(), a.getDebtorEmail(),
                a.getDebtorPhone(), a.getDebtorAddress(), a.getOriginalCreditorName(), a.getOriginalDebtDate(),
                a.getOriginalDebtAmount(), a.getCurrentBalance(), a.getStatus(), a.getAssignedCollectorId(),
                a.getPlacedDate(), a.getClosedDate(), a.getNotes());
    }

    private ContactLogResponse toContactResponse(CollAgencyContactLog l) {
        return new ContactLogResponse(l.getId(), l.getDebtorAccountId(), l.getContactDate(), l.getContactMethod(),
                l.getOutcome(), l.isDisclosedThirdPartyCollector(), l.isDisclosedOriginalCreditor(),
                l.isDisclosedDebtorRights(), l.getNotes(), l.getPromisedPaymentDate(), l.getPromisedPaymentAmount(),
                l.getRecordedByUserId(), l.getRecordedByUserName());
    }

    private PaymentPlanResponse toPlanResponse(CollAgencyPaymentPlan p) {
        return new PaymentPlanResponse(p.getId(), p.getDebtorAccountId(), p.getStatus(), p.getTotalAgreedAmount(),
                p.getInstallmentAmount(), p.getFrequency(), p.getStartDate(), p.getNextDueDate(),
                p.getNumberOfInstallments(), p.getInstallmentsPaid(), p.getNotes());
    }
}
