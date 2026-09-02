package za.co.handyflow.platform.debtcollection.api;

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
import za.co.handyflow.platform.debtcollection.application.internal.CollectionContactLogService;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionCaseService;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionPdfService;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debt collection cases — the core resource. Contact-log and evidence
 * endpoints are nested here as case sub-resources (same "module may wrap
 * the generic endpoint" convention EvidenceController invites, already
 * used by LitigationMatterController/DsarRequestController in Module 1).
 * PaymentPlan creation/listing is also nested here (a plan only ever
 * exists in the context of a case); plan state-transition endpoints
 * (mark-installment-paid/mark-defaulted/cancel) live on
 * PaymentPlanController since they're addressed by planId, not caseId.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/cases")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Cases", description = "Internal debt collection cases")
public class DebtCollectionCaseController {

    private static final String EVIDENCE_ENTITY_TYPE = "DebtCollectionCase";

    private final DebtCollectionCaseService caseService;
    private final CollectionContactLogService contactLogService;
    private final za.co.handyflow.platform.debtcollection.application.internal.PaymentPlanService paymentPlanService;
    private final EvidenceFacade evidenceFacade;
    private final DebtCollectionPdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<Page<DebtCollectionCaseResponse>>> list(
            @RequestParam(required = false) CaseStatus status,
            @PageableDefault(size = 20, sort = "openedDate") Pageable pageable) {
        featureGuard.requireModule("debtcollection");
        Page<DebtCollectionCaseResponse> page = caseService
                .list(TenantContext.getTenantIdAsObject(), status, pageable)
                .map(DebtCollectionCaseResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        featureGuard.requireModule("debtcollection");
        long count = caseService.count(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(DebtCollectionCaseResponse.of(c)));
    }

    @GetMapping("/outstanding-invoices")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Outstanding invoices for a customer, to pick from before opening a case")
    public ResponseEntity<ApiResponse<List<OutstandingInvoiceResponse>>> outstandingInvoicesForCustomer(
            @RequestParam UUID customerId) {
        featureGuard.requireModule("debtcollection");
        List<OutstandingInvoiceResponse> invoices = caseService
                .findOutstandingInvoicesForCustomer(TenantContext.getTenantIdAsObject(), customerId).stream()
                .map(OutstandingInvoiceResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Open a case manually — assigns a case number, computes totalOutstanding from the given invoices")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> open(
            @Valid @RequestBody CreateDebtCollectionCaseRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.open(
                TenantContext.getTenantIdAsObject(), req.customerId(), req.debtorName(), req.debtorEmail(),
                req.debtorPhone(), req.invoiceIds(), req.openedDate(), req.assignedToUserId(),
                req.assignedToUserName(), req.notes(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case opened", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/open-for-customer")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Open a case from a CRM customer — pulls debtor contact details and all their outstanding invoices automatically")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> openForCustomer(
            @Valid @RequestBody OpenCaseForCustomerRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.openForCustomer(
                TenantContext.getTenantIdAsObject(), req.customerId(), req.openedDate(), req.assignedToUserId(),
                req.assignedToUserName(), req.notes(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case opened", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/refresh-outstanding")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Recompute totalOutstanding from current invoicing data")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> refreshOutstanding(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.refreshOutstanding(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Outstanding balance refreshed", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/link-invoice")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> linkInvoice(
            @PathVariable UUID id, @Valid @RequestBody LinkInvoiceRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.linkInvoice(TenantContext.getTenantIdAsObject(), id, req.invoiceId());
        return ResponseEntity.ok(ApiResponse.success("Invoice linked", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/unlink-invoice")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> unlinkInvoice(
            @PathVariable UUID id, @Valid @RequestBody LinkInvoiceRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.unlinkInvoice(TenantContext.getTenantIdAsObject(), id, req.invoiceId());
        return ResponseEntity.ok(ApiResponse.success("Invoice unlinked", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> assign(
            @PathVariable UUID id, @Valid @RequestBody AssignCaseRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.assign(TenantContext.getTenantIdAsObject(), id, req.userId(), req.userName());
        return ResponseEntity.ok(ApiResponse.success("Case assigned", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/schedule-next-action")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> scheduleNextAction(
            @PathVariable UUID id, @Valid @RequestBody ScheduleNextActionRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.scheduleNextAction(TenantContext.getTenantIdAsObject(), id, req.nextActionDate());
        return ResponseEntity.ok(ApiResponse.success("Next action scheduled", DebtCollectionCaseResponse.of(c)));
    }

    @PutMapping("/{id}/notes")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> updateNotes(
            @PathVariable UUID id, @RequestBody UpdateCaseNotesRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.updateNotes(TenantContext.getTenantIdAsObject(), id, req.notes());
        return ResponseEntity.ok(ApiResponse.success("Notes updated", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/advance-status")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> advanceStatus(
            @PathVariable UUID id, @Valid @RequestBody AdvanceCaseStatusRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.advanceStatus(TenantContext.getTenantIdAsObject(), id, req.status());
        return ResponseEntity.ok(ApiResponse.success("Status updated", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Formally write off the debt as uncollectable — a financial determination, restricted to DEBTCOLLECTION_ADMIN")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> writeOff(
            @PathVariable UUID id, @Valid @RequestBody WriteOffCaseRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.writeOff(TenantContext.getTenantIdAsObject(), id, req.amount(), req.reason());
        return ResponseEntity.ok(ApiResponse.success("Case written off", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> close(
            @PathVariable UUID id, @Valid @RequestBody CloseCaseRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.close(TenantContext.getTenantIdAsObject(), id, req.reason(), req.outcomeNotes());
        return ResponseEntity.ok(ApiResponse.success("Case closed", DebtCollectionCaseResponse.of(c)));
    }

    @PostMapping("/{id}/link-contract")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<DebtCollectionCaseResponse>> linkContract(
            @PathVariable UUID id, @Valid @RequestBody LinkContractRequest req) {
        featureGuard.requireModule("debtcollection");
        DebtCollectionCase c = caseService.linkContract(TenantContext.getTenantIdAsObject(), id, req.contractId());
        return ResponseEntity.ok(ApiResponse.success("Contract linked", DebtCollectionCaseResponse.of(c)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        caseService.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Case deleted", null));
    }

    // ── Contact log (compliance trail) ──────────────────────────────────────

    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a contact attempt against this case")
    public ResponseEntity<ApiResponse<CollectionContactLogResponse>> recordContact(
            @PathVariable UUID id, @Valid @RequestBody RecordContactRequest req) {
        featureGuard.requireModule("debtcollection");
        CollectionContactLog log = contactLogService.record(
                TenantContext.getTenantIdAsObject(), id, req.contactDate(), req.contactMethod(), req.outcome(),
                req.notes(), req.promisedPaymentDate(), req.promisedPaymentAmount(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contact recorded", CollectionContactLogResponse.of(log)));
    }

    @GetMapping("/{id}/contacts")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<CollectionContactLogResponse>>> listContacts(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        List<CollectionContactLogResponse> logs = contactLogService
                .listForCase(TenantContext.getTenantIdAsObject(), id).stream()
                .map(CollectionContactLogResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    // ── Payment plans ────────────────────────────────────────────────────────

    @PostMapping("/{id}/payment-plans")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Propose a structured repayment plan — also moves the case to PAYMENT_PLAN_ACTIVE")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> proposePlan(
            @PathVariable UUID id, @Valid @RequestBody ProposePaymentPlanRequest req) {
        featureGuard.requireModule("debtcollection");
        var plan = paymentPlanService.propose(
                TenantContext.getTenantIdAsObject(), id, req.totalAgreedAmount(), req.installmentAmount(),
                req.frequency(), req.startDate(), req.numberOfInstallments(), req.notes(),
                TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment plan proposed", PaymentPlanResponse.of(plan)));
    }

    @GetMapping("/{id}/payment-plans")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentPlanResponse>>> listPlans(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        List<PaymentPlanResponse> plans = paymentPlanService
                .listForCase(TenantContext.getTenantIdAsObject(), id).stream()
                .map(PaymentPlanResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    // ── Evidence (demand letters, AOD, correspondence, ...) ─────────────────

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Attach a document (demand letter, AOD, correspondence, ...) to this case")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String evidenceType) {
        featureGuard.requireModule("debtcollection");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        caseService.get(tenantId, id); // 404s if the case doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "debtcollection",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        caseService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, "debtcollection", EVIDENCE_ENTITY_TYPE, id)));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Export the full case register as a PDF")
    public ResponseEntity<byte[]> exportPdf() {
        featureGuard.requireModule("debtcollection");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = pdfService.generateCaseRegister(null, caseService.listAll(tenantId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"debt-collection-case-register.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{id}/demand-letter/pdf")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Generate a formal demand letter PDF for this case")
    public ResponseEntity<byte[]> demandLetterPdf(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        DebtCollectionCase c = caseService.get(tenantId, id);
        byte[] pdf = pdfService.generateDemandLetter(null, c);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demand-letter-" + c.getCaseNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
