package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.application.PostingKeys;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.ap.domain.ApPaymentRules;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.model.ApCreditAllocation;
import za.co.handyflow.platform.ap.domain.model.ApCreditNote;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApCreditAllocationRepository;
import za.co.handyflow.platform.ap.domain.repository.ApCreditNoteRepository;
import za.co.handyflow.platform.ap.dto.AllocateApCreditRequest;
import za.co.handyflow.platform.ap.dto.ApCreditNoteResponse;
import za.co.handyflow.platform.ap.dto.CreateApCreditNoteRequest;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

/**
 * Credit notes received from suppliers (finance patch F10, 2026-10-14).
 * <p>
 * A credit note reduces what is owed to the supplier: Dr Accounts payable, Cr the expense it reverses, Cr VAT input. It
 * goes to the approval engine: below the limit (platform default R 5,000 incl. VAT) it is issued at once; above it, it
 * waits for someone with AP_APPROVE, who is never the person who captured it unless nobody else can approve. Once
 * issued it is applied to the bill it names (up to what that bill still owes); the rest, or a credit note with no bill,
 * stays as credit held with the supplier and is applied to bills later. Applying credit to a bill is inside Accounts
 * Payable, so it posts nothing further.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApCreditNoteService {

    private static final String MODULE = "ap", ENTITY = "CREDIT_NOTE";
    private static final String VAT_INPUT_ACCOUNT_CODE = "1300";

    private final ApCreditNoteRepository       creditRepo;
    private final ApCreditAllocationRepository allocationRepo;
    private final ApBillRepository             billRepo;
    private final AccountingFacade             accountingFacade;
    private final ApprovalFacade               approvalFacade;
    private final ApDutyChecks                 duties;
    private final JdbcTemplate                 jdbc;

    // ── Reading ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApCreditNoteResponse> list(TenantId tenantId) {
        return creditRepo.findAllForTenant(tenantId).stream().map(c -> toResponse(tenantId, c, false)).toList();
    }

    @Transactional(readOnly = true)
    public ApCreditNoteResponse get(TenantId tenantId, UUID id) {
        return toResponse(tenantId, find(tenantId, id), true);
    }

    // ── Capturing ─────────────────────────────────────────────────────────────

    /** Captures a supplier's credit note; issued at once when small, otherwise waiting for approval. */
    @Transactional
    public ApCreditNoteResponse create(TenantId tenantId, CreateApCreditNoteRequest req, UUID createdBy) {
        if (req.creditNoteNumber() == null || req.creditNoteNumber().isBlank())
            throw bad("Enter the supplier's credit note number", "CREDIT_NUMBER_REQUIRED");
        if (req.reason() == null || req.reason().trim().length() < 5)
            throw bad("Say what the credit is for (at least 5 characters)", "CREDIT_REASON_REQUIRED");
        String amountProblem = ApPaymentRules.creditAmountsProblem(req.amount(), req.vatAmount());
        if (amountProblem != null) throw bad(amountProblem, "CREDIT_AMOUNT_INVALID");

        ApBill bill = null;
        String supplierName = req.supplierName() != null ? req.supplierName().trim() : "";
        String category = req.category();
        if (req.billId() != null) {
            bill = billRepo.findByIdAndTenantId(req.billId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill", req.billId().toString()));
            if ("DRAFT".equals(bill.getStatus()) || "REJECTED".equals(bill.getStatus())
                    || "CANCELLED".equals(bill.getStatus()) || "SECOND_APPROVAL".equals(bill.getStatus()))
                throw bad("A credit note can only be raised against a bill that has been approved", "BILL_NOT_APPROVED");
            supplierName = bill.getSupplierName();
            category = bill.getCategory();
        } else if (supplierName.isEmpty() || !knownSupplier(tenantId, supplierName)) {
            throw bad("Choose a supplier that has bills here, or the bill this credit note is for", "SUPPLIER_UNKNOWN");
        }
        if (creditRepo.existsLive(tenantId, supplierName, req.creditNoteNumber().trim()))
            throw new HandyFlowException("Credit note " + req.creditNoteNumber().trim() + " from " + supplierName + " is already captured",
                    HttpStatus.CONFLICT, "DUPLICATE_CREDIT_NOTE");

        ApCreditNote cn = ApCreditNote.create(tenantId, req.creditNoteNumber().trim(),
                bill != null ? bill.getSupplierId() : null, supplierName, bill != null ? bill.getId() : null,
                category, req.creditDate(), req.reason().trim(), req.amount(), req.vatAmount(), createdBy);

        if (bill != null) {
            BigDecimal already = creditRepo.findLiveByBill(tenantId, bill.getId()).stream()
                    .map(ApCreditNote::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String tooMuch = ApPaymentRules.creditNoteProblem(bill.getTotalAmount(), already, cn.getTotalAmount());
            if (tooMuch != null) throw bad(tooMuch, "CREDIT_MORE_THAN_BILL");
        }
        creditRepo.save(cn);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("totalAmount", cn.getTotalAmount());
        if (cn.getBillId() != null) metadata.put("billId", cn.getBillId().toString());
        if (createdBy != null && duties.otherHolders(tenantId, "AP_APPROVE", createdBy) > 0) {
            metadata.put("excludedActors", List.of(createdBy.toString()));
        }
        ApprovalRequestResponse request = approvalFacade.submit(tenantId, MODULE, ENTITY, cn.getId(), createdBy, metadata);
        if ("APPROVED".equals(request.status())) {
            issueNow(tenantId, cn, null);
        } else {
            log.info("[AP] Supplier credit note={} supplier={} total={} waiting for approval",
                    cn.getCreditNoteNumber(), cn.getSupplierName(), cn.getTotalAmount());
        }
        return toResponse(tenantId, cn, true);
    }

    // ── Approving ─────────────────────────────────────────────────────────────

    /** Gives this person's approval; the credit note is issued when the last step is done. */
    @Transactional
    public ApCreditNoteResponse approve(TenantId tenantId, UUID id, UUID by) {
        ApCreditNote cn = pending(tenantId, id);
        ApprovalRequestResponse result = actOnPendingStep(tenantId, cn, by, "APPROVE", null);
        if ("APPROVED".equals(result.status())) completeApproval(tenantId, id, by);
        return get(tenantId, id);
    }

    /** Rejects a credit note waiting for approval, with the reason. */
    @Transactional
    public ApCreditNoteResponse reject(TenantId tenantId, UUID id, UUID by, String reason) {
        if (reason == null || reason.trim().length() < 5)
            throw bad("Say why the credit note is rejected (at least 5 characters)", "REJECT_REASON_REQUIRED");
        ApCreditNote cn = pending(tenantId, id);
        actOnPendingStep(tenantId, cn, by, "REJECT", reason.trim());
        rejectFromEngine(tenantId, id, by, reason.trim());
        return get(tenantId, id);
    }

    /** Issues an approved credit note, once (also called when approval finishes in the Approvals inbox). */
    @Transactional
    public void completeApproval(TenantId tenantId, UUID id, UUID by) {
        ApCreditNote cn = creditRepo.findByIdAndTenantId(id, tenantId).orElse(null);
        if (cn == null || !"PENDING_APPROVAL".equals(cn.getStatus())) return;
        issueNow(tenantId, cn, by);
    }

    /** Rejects, once (also called when rejection happens in the Approvals inbox). */
    @Transactional
    public void rejectFromEngine(TenantId tenantId, UUID id, UUID by, String reason) {
        ApCreditNote cn = creditRepo.findByIdAndTenantId(id, tenantId).orElse(null);
        if (cn == null || !"PENDING_APPROVAL".equals(cn.getStatus())) return;
        cn.reject(by, reason != null ? reason : rejectionComment(tenantId, id));
        creditRepo.save(cn);
        log.info("[AP] Supplier credit note={} rejected by={}", cn.getCreditNoteNumber(), by);
    }

    // ── Applying credit to bills ──────────────────────────────────────────────

    /**
     * Applies the credit still held on {@code id} to the supplier's bills: the lines given, or oldest due first when none
     * are given. Stops at what each bill owes and at the credit left.
     */
    @Transactional
    public ApCreditNoteResponse allocate(TenantId tenantId, UUID id, AllocateApCreditRequest req, UUID by) {
        ApCreditNote cn = find(tenantId, id);
        if (!"ISSUED".equals(cn.getStatus())) throw bad("Only an issued credit note can be applied to bills", "CREDIT_NOT_ISSUED");
        BigDecimal available = cn.getAvailable();
        if (available.signum() <= 0) throw bad("This credit note has no credit left to apply", "CREDIT_USED_UP");

        List<ApBill> open = billRepo.findOpenForSupplier(tenantId, cn.getSupplierName());
        Map<UUID, BigDecimal> openBalances = new LinkedHashMap<>();
        for (ApBill b : open) if (b.getBalance().signum() > 0) openBalances.put(b.getId(), b.getBalance());

        List<ApPaymentRules.Allocation> lines;
        if (req == null || req.lines() == null || req.lines().isEmpty()) {
            List<ApPaymentRules.Open> opens = open.stream().filter(b -> b.getBalance().signum() > 0)
                    .map(b -> new ApPaymentRules.Open(b.getId(), b.getBalance())).toList();
            lines = ApPaymentRules.oldestFirst(available, opens);
            if (lines.isEmpty()) throw bad("This supplier has no open bills to apply the credit to", "NO_OPEN_BILLS");
        } else {
            lines = req.lines().stream().map(l -> new ApPaymentRules.Allocation(l.billId(), l.amount())).toList();
        }
        String problem = ApPaymentRules.allocationProblem(lines, available, openBalances);
        if (problem != null) throw bad(problem, "ALLOCATION_NOT_ALLOWED");

        Map<UUID, ApBill> byId = new HashMap<>();
        open.forEach(b -> byId.put(b.getId(), b));
        for (ApPaymentRules.Allocation line : lines) {
            applyToBill(tenantId, cn, byId.get(line.billId()), ApPaymentRules.money(line.amount()), by);
        }
        creditRepo.save(cn);
        return get(tenantId, id);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Posts the credit note once, marks it issued, and applies it to the bill it names (up to what that bill owes). */
    private void issueNow(TenantId tenantId, ApCreditNote cn, UUID by) {
        UUID journalId = postCreditJournal(tenantId, cn);
        cn.issue(by, journalId);
        creditRepo.save(cn);

        if (cn.getBillId() != null) {
            ApBill bill = billRepo.findByIdAndTenantId(cn.getBillId(), tenantId).orElse(null);
            if (bill != null && ApPaymentRules.payable(bill.getStatus())) {
                BigDecimal[] split = ApPaymentRules.splitCredit(cn.getTotalAmount(), bill.getBalance());
                if (split[0].signum() > 0) applyToBill(tenantId, cn, bill, split[0], by);
            }
        }
        log.info("[AP] Issued supplier credit note={} supplier={} total={} kept as credit={}",
                cn.getCreditNoteNumber(), cn.getSupplierName(), cn.getTotalAmount(), cn.getAvailable());
    }

    private void applyToBill(TenantId tenantId, ApCreditNote cn, ApBill bill, BigDecimal amount, UUID by) {
        bill.applyCredit(amount);
        billRepo.save(bill);
        cn.allocate(amount);
        allocationRepo.save(ApCreditAllocation.of(tenantId, cn.getId(), bill.getId(), amount, by));
    }

    /** Dr Accounts payable (total), Cr the expense it reverses, Cr VAT input. Posted once per credit note. */
    private UUID postCreditJournal(TenantId tenantId, ApCreditNote cn) {
        String expenseCode = ApService.expenseAccountCode(cn.getCategory());
        UUID expenseId = account(tenantId, expenseCode);
        UUID apId = account(tenantId, "2010");
        boolean hasVat = cn.getVatAmount().signum() > 0;
        UUID vatId = hasVat ? account(tenantId, VAT_INPUT_ACCOUNT_CODE) : null;

        List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
        lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                apId, "Credit note " + cn.getCreditNoteNumber() + " - " + cn.getSupplierName(), cn.getTotalAmount(), null));
        lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                expenseId, cn.getCategory() + " expense reversed - " + cn.getSupplierName(), null, cn.getAmount()));
        if (hasVat) {
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    vatId, "VAT input reversed - " + cn.getSupplierName(), null, cn.getVatAmount()));
        }
        CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                cn.getCreditDate(),
                "AP supplier credit note: " + cn.getCreditNoteNumber() + " - " + cn.getSupplierName(),
                cn.getCreditNoteNumber(), "MANUAL", lines);
        return accountingFacade.postOnce(tenantId, PostingKeys.of("ap", "credit-note", cn.getId()), req).id();
    }

    private UUID account(TenantId tenantId, String code) {
        try {
            UUID id = jdbc.queryForObject(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code = ? AND active = true LIMIT 1",
                    UUID.class, tenantId.getValue(), code);
            if (id != null) return id;
        } catch (Exception ignored) { /* reported below */ }
        throw new HandyFlowException("Chart of Accounts is missing account " + code + " - cannot post the credit note",
                HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_SEEDED");
    }

    private boolean knownSupplier(TenantId tenantId, String name) {
        return billRepo.findDistinctSupplierNames(tenantId).stream().anyMatch(n -> n != null && n.equalsIgnoreCase(name));
    }

    private ApCreditNote find(TenantId tenantId, UUID id) {
        return creditRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier credit note", id.toString()));
    }

    private ApCreditNote pending(TenantId tenantId, UUID id) {
        ApCreditNote cn = find(tenantId, id);
        if (!"PENDING_APPROVAL".equals(cn.getStatus()))
            throw bad("Only a credit note waiting for approval can be approved or rejected", "CREDIT_NOT_PENDING");
        return cn;
    }

    private ApprovalRequestResponse actOnPendingStep(TenantId tenantId, ApCreditNote cn, UUID actor, String decision, String comment) {
        ApprovalRequestResponse request = approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, cn.getId())
                .filter(r -> "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status()))
                .orElseThrow(() -> bad("This credit note has no approval waiting", "NO_APPROVAL_WAITING"));
        ApprovalStepResponse step = request.steps().stream()
                .filter(st -> "PENDING".equals(st.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder))
                .orElseThrow(() -> bad("This credit note's approval has no step waiting", "NO_APPROVAL_WAITING"));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> authorities = auth == null ? List.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return approvalFacade.actOnStep(tenantId, step.id(), actor, authorities, decision, comment, null);
    }

    private String rejectionComment(TenantId tenantId, UUID id) {
        return approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, id)
                .flatMap(r -> r.steps().stream().filter(st -> "REJECTED".equals(st.status())).findFirst())
                .map(ApprovalStepResponse::comment).orElse(null);
    }

    private ApCreditNoteResponse toResponse(TenantId tenantId, ApCreditNote c, boolean withAllocations) {
        String billNumber = c.getBillId() == null ? null
                : billRepo.findByIdAndTenantId(c.getBillId(), tenantId).map(ApBill::getBillNumber).orElse(null);
        List<ApCreditNoteResponse.Allocation> allocations = !withAllocations ? List.of()
                : allocationRepo.findByCreditNote(tenantId, c.getId()).stream()
                        .map(a -> new ApCreditNoteResponse.Allocation(a.getBillId(),
                                billRepo.findByIdAndTenantId(a.getBillId(), tenantId).map(ApBill::getBillNumber).orElse(null),
                                a.getAmount(), a.getAllocatedAt()))
                        .toList();
        return new ApCreditNoteResponse(c.getId(), c.getCreditNoteNumber(), c.getSupplierId(), c.getSupplierName(),
                c.getBillId(), billNumber, c.getCategory(), c.getCreditDate(), c.getReason(),
                c.getAmount(), c.getVatAmount(), c.getTotalAmount(), c.getAllocated(), c.getAvailable(),
                c.getStatus(), c.getRejectionReason(), c.getJournalEntryId(),
                c.getCreatedBy(), c.getDecidedBy(), c.getDecidedAt(), c.getCreatedAt(), allocations);
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }
}
