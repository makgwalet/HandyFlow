package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.*;
import za.co.handyflow.platform.ap.domain.repository.*;
import za.co.handyflow.platform.ap.dto.*;
import za.co.handyflow.platform.shared.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * FIX: backlog 1.1b — approveBill() is now resubmission-aware. Your
 * confirmed decisions: a real REJECTED status distinct from CANCELLED,
 * and the same bill is editable and resubmittable, not terminal. Uses
 * ApprovalFacade.resubmit() (widened this session to accept REJECTED,
 * not just RETURNED_FOR_CORRECTION) so the new ApprovalRequest stays
 * linked to the rejected one via resubmittedFromId — a real audit
 * trail, not a disconnected fresh history for a bill that's genuinely
 * been through this before.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApService {

    private final ApBillRepository      billRepo;
    private final ApEftBatchRepository  batchRepo;
    private final ApBatchItemRepository batchItemRepo;
    private final ApSupplierBankingRepository supplierBankingRepo;
    private final JdbcTemplate          jdbc;
    private final AccountingFacade      accountingFacade;
    private final NotificationService   notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    // FIX: backlog 1.1 — the shared approval engine. See approveBill()'s
    // own comment block below for the full migration rationale.
    private final ApprovalFacade        approvalFacade;

    // ── Category → expense account code mapping ───────────────────────────────
    private static final Map<String, String> CATEGORY_ACCOUNT = Map.ofEntries(
            Map.entry("RENT",             "5120"),
            Map.entry("UTILITIES",        "5130"),
            Map.entry("FUEL",             "5140"),
            Map.entry("SALARY",           "5110"),
            Map.entry("PROFESSIONAL_FEES","5190"),
            Map.entry("EQUIPMENT",        "5530"),
            Map.entry("MAINTENANCE",      "5150"),
            Map.entry("INSURANCE",        "5200"),
            Map.entry("SUBSCRIPTIONS",    "5160"),
            Map.entry("MARKETING",        "5180"),
            Map.entry("OTHER",            "5100")
    );

    // WHY a dedicated constant, not baked into the SQL string? Same
    // reasoning as CATEGORY_ACCOUNT above — one place to change if this
    // tenant's Chart of Accounts ever uses a different code for VAT
    // Input/Claimable.
    private static final String VAT_INPUT_ACCOUNT_CODE = "1300";

    // NO LONGER READ DIRECTLY BY approveBill() — the threshold now lives
    // as data on the platform-default ApprovalRule seeded by the
    // approvals engine's own migration (V246), so it's tenant-editable
    // instead of a fixed property. Left here, unused, deliberately not
    // deleted: dropping a @Value-backed property is a more consequential
    // change than leaving an unused field, and someone may still be
    // relying on the ap.approval.second-approval-threshold config key
    // existing even if nothing reads it anymore right now.
    @Value("${ap.approval.second-approval-threshold:10000}")
    private BigDecimal secondApprovalThreshold;

    // ── Bills ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BillResponse> getBills(TenantId tenantId, String status, Pageable pageable) {
        return billRepo.findAll(tenantId, status, pageable).map(this::toBillResponse);
    }

    @Transactional(readOnly = true)
    public BillResponse getBill(TenantId tenantId, UUID id) {
        return toBillResponse(findBill(tenantId, id));
    }

    @Transactional
    public BillResponse createBill(TenantId tenantId, UUID createdBy, CreateBillRequest req) {
        if (billRepo.existsByTenantIdAndBillNumber(tenantId, req.billNumber())) {
            throw new HandyFlowException(
                    "Bill number '" + req.billNumber() + "' already exists",
                    HttpStatus.BAD_REQUEST, "DUPLICATE_BILL");
        }

        // Possible-duplicate WARNING (not a block) — same supplier, same
        // total amount, within a tight date window. See
        // ApBillRepository.findPossibleDuplicates()'s own comment for why
        // this is deliberately narrow enough not to flag genuinely
        // recurring bills (rent, salaries) at the same amount.
        BigDecimal vat = req.vatAmount() != null ? req.vatAmount() : BigDecimal.ZERO;
        BigDecimal totalAmount = req.amount().add(vat);
        List<ApBill> possibleDuplicates = billRepo.findPossibleDuplicates(
                tenantId, req.supplierName(), totalAmount,
                req.billDate().minusDays(10), req.billDate().plusDays(10));
        String duplicateWarning = null;
        if (!possibleDuplicates.isEmpty()) {
            ApBill match = possibleDuplicates.get(0);
            duplicateWarning = "Possible duplicate — " + req.supplierName()
                    + " already has bill #" + match.getBillNumber()
                    + " for the same amount (R " + totalAmount + ") dated " + match.getBillDate();
            log.warn("Possible duplicate bill on create: new supplier={} amount={} date={} matches existing bill={}",
                    req.supplierName(), totalAmount, req.billDate(), match.getId());
        }

        ApBill bill = ApBill.create(
                tenantId, req.supplierId(), req.supplierName(),
                req.billNumber(), req.billDate(), req.dueDate(),
                req.category(), req.description(),
                req.amount(), req.vatAmount(),
                req.attachmentBase64(), req.attachmentName(),
                req.notes(), createdBy);

        billRepo.save(bill);
        log.info("Created AP bill={} supplier={} amount={}", bill.getId(),
                req.supplierName(), req.amount());

        notifyBillPendingApproval(tenantId, bill);

        return toBillResponse(bill, duplicateWarning);
    }

    /**
     * FIX: backlog 1.1b — guard widened to also allow REJECTED, not just
     * DRAFT. A rejected bill needs to be editable so it can actually be
     * corrected before resubmission — see approveBill()'s own comment
     * for the full resubmission flow this unlocks.
     */
    @Transactional
    public BillResponse updateBill(TenantId tenantId, UUID id, UpdateBillRequest req) {
        ApBill bill = findBill(tenantId, id);
        if (!"DRAFT".equals(bill.getStatus()) && !"REJECTED".equals(bill.getStatus())) {
            throw new HandyFlowException("Only DRAFT or REJECTED bills can be edited",
                    HttpStatus.BAD_REQUEST, "BILL_NOT_EDITABLE");
        }
        // Use reflection-free approach via JPQL update or direct field access
        // Since domain model has no setters, use a new create pattern isn't ideal
        // Instead use JDBC for the update on draft bills
        jdbc.update("""
            UPDATE ap_bills SET
                supplier_name = COALESCE(?, supplier_name),
                bill_number   = COALESCE(?, bill_number),
                bill_date     = COALESCE(?, bill_date),
                due_date      = COALESCE(?, due_date),
                category      = COALESCE(?, category),
                description   = COALESCE(?, description),
                amount        = COALESCE(?, amount),
                vat_amount    = COALESCE(?, vat_amount),
                total_amount  = COALESCE(?, amount, amount) + COALESCE(?, vat_amount, vat_amount),
                notes         = COALESCE(?, notes),
                updated_at    = now()
            WHERE id = ? AND tenant_id = ?
            """,
                req.supplierName(), req.billNumber(),
                req.billDate(), req.dueDate(),
                req.category(), req.description(),
                req.amount(), req.vatAmount(),
                req.amount(), req.vatAmount(),
                req.notes(),
                id, tenantId.getValue());

        return toBillResponse(findBill(tenantId, id));
    }

    /**
     * FIX: backlog 1.1 — migrated onto the shared approval engine.
     * FIX: backlog 1.1b — now resubmission-aware. If this bill is
     * currently REJECTED and has a prior REJECTED approval request on
     * file, this call is treated as a genuine resubmission (the bill has
     * been edited via updateBill() and is being sent back through
     * approval) rather than a fresh first approval — resets the bill to
     * DRAFT and calls resubmit() instead of submit(), keeping the new
     * ApprovalRequest linked to the rejected one via resubmittedFromId.
     * <p>
     * completeApprovalAndPostJournal() is called directly here (not left
     * purely to ApApprovalEventHandler's async listener) so the HTTP
     * caller gets back a bill that's ALREADY APPROVED with a real
     * journal reference in the same response — @ApplicationModuleListener
     * runs after this transaction commits, which would otherwise mean
     * the immediate response still shows the pre-approval status. The
     * listener also calls the same method as a second, idempotent path
     * (guarded — see that method) so approving via the new generic
     * /api/v1/approvals/steps/{id}/approve endpoint instead of this one
     * still posts the journal correctly.
     */
    @Transactional
    public BillResponse approveBill(TenantId tenantId, UUID id, UUID approvedBy) {
        ApBill bill = findBill(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, "ap", "BILL", id);
        ApprovalRequestResponse approvalResult;

        if ("REJECTED".equals(bill.getStatus()) && existing.isPresent()
                && "REJECTED".equals(existing.get().status())) {
            // FIX: backlog 1.1b — resubmission of a previously rejected,
            // now-edited bill. See this method's own class-level comment.
            bill.backToDraftForResubmission();
            billRepo.save(bill);
            approvalResult = approvalFacade.resubmit(tenantId, existing.get().id(), approvedBy,
                    Map.of("totalAmount", bill.getTotalAmount()));
            if (isOpen(approvalResult)) {
                ApprovalStepResponse firstStep = firstPendingStep(approvalResult)
                        .orElseThrow(() -> new IllegalStateException("A freshly-resubmitted request has no pending step"));
                approvalResult = approvalFacade.actOnStep(tenantId, firstStep.id(), approvedBy,
                        currentUserAuthorities(), "APPROVE", null, null);
            }
        } else if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPendingStep(existing.get())
                    .orElseThrow(() -> new HandyFlowException(
                            "This bill's approval request has no pending step — data inconsistency, needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            approvalResult = approvalFacade.actOnStep(tenantId, pendingStep.id(), approvedBy,
                    currentUserAuthorities(), "APPROVE", null, null);
        } else {
            approvalResult = approvalFacade.submit(tenantId, "ap", "BILL", id, approvedBy,
                    Map.of("totalAmount", bill.getTotalAmount()));
            // submit() only creates the steps, it doesn't act on any of
            // them — this very call is also the first approver's action,
            // so act on step 1 immediately rather than requiring a
            // second, separate click for what the user experienced as
            // one "Approve" action.
            if (isOpen(approvalResult)) {
                ApprovalStepResponse firstStep = firstPendingStep(approvalResult)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                approvalResult = approvalFacade.actOnStep(tenantId, firstStep.id(), approvedBy,
                        currentUserAuthorities(), "APPROVE", null, null);
            }
        }

        if ("APPROVED".equals(approvalResult.status())) {
            completeApprovalAndPostJournal(tenantId, id);
        } else if ("REJECTED".equals(approvalResult.status())) {
            // FIX: backlog 1.1b — real rejection handling. Reloads the
            // bill since the resubmission branch above may already have
            // mutated it (backToDraftForResubmission()) earlier in this
            // same call.
            ApBill freshBill = findBill(tenantId, id);
            freshBill.reject(findRejectionReason(tenantId, id));
            billRepo.save(freshBill);
            log.info("Bill={} tenant={} rejected via approval engine", id, tenantId);
        } else {
            // Still IN_PROGRESS — first approval recorded, further
            // approval(s) still pending per this bill's approval rule.
            //
            // requestSecondApproval()'s own guard only accepts DRAFT/
            // OVERDUE as the prior state, so this only fires on the FIRST
            // step of an in-flight request — correct for AP's actual
            // seeded rule (exactly two steps), but worth being honest
            // about the limit: if a tenant ever configures a 3+-step rule
            // for AP bills (possible now that rules are tenant-editable —
            // see backlog 1.1 Q3), a bill already in SECOND_APPROVAL has
            // no further ApBill status to move into for step 3 onward —
            // ApBill's status enum has no room for "third of four
            // approvals still pending." Guarding rather than letting that
            // hit requestSecondApproval()'s own IllegalStateException.
            if ("DRAFT".equals(bill.getStatus()) || "OVERDUE".equals(bill.getStatus())) {
                bill.requestSecondApproval(approvedBy);
                billRepo.save(bill);
            } else {
                log.info("Bill={} received another approval beyond the second — ApBill's status has no representation for this; status unchanged", id);
            }
            // Same notification already used for a brand-new pending
            // bill; its phrasing already reads correctly for "still needs
            // one more approval" too.
            notifyBillPendingApproval(tenantId, bill);
            log.info("Bill={} received one approval from={}, still awaiting further approval(s) per its approval rule",
                    id, approvedBy);
        }

        return toBillResponse(findBill(tenantId, id)); // reload — journal posting above may have changed status
    }

    /**
     * FIX: backlog 1.1. Extracted from the old approveBill()'s inline
     * "below threshold" and "second approval received" branches —
     * unchanged logic, just now callable from two places (directly above,
     * and from ApApprovalEventHandler for approvals completed via the
     * generic engine endpoint). Idempotency guard makes both call sites
     * safe together: whichever one runs first does the real work, the
     * other becomes a no-op.
     */
    @Transactional
    public void completeApprovalAndPostJournal(TenantId tenantId, UUID billId) {
        ApBill bill = findBill(tenantId, billId);
        if ("APPROVED".equals(bill.getStatus()) || "PAID".equals(bill.getStatus())
                || "OVERDUE".equals(bill.getStatus())) {
            return; // already completed by the other call path
        }
        String expenseAccount = CATEGORY_ACCOUNT.getOrDefault(bill.getCategory(), "5100");
        UUID journalId = postApprovalJournal(tenantId, bill, expenseAccount);
        bill.approve(journalId);
        billRepo.save(bill);
        log.info("Approved bill={} journal={}", billId, journalId);
    }

    /**
     * FIX: backlog 1.1b — public wrapper for ApApprovalEventHandler,
     * same idempotency reasoning as completeApprovalAndPostJournal():
     * safe to call even if approveBill()'s own REJECTED branch already
     * handled it directly (the HTTP-caller path runs first and commits
     * before the async listener fires).
     */
    @Transactional
    public void rejectBillFromEngine(TenantId tenantId, UUID billId) {
        ApBill bill = findBill(tenantId, billId);
        if ("REJECTED".equals(bill.getStatus())) return; // already handled by the other call path
        bill.reject(findRejectionReason(tenantId, billId));
        billRepo.save(bill);
        log.info("[AP] Bill={} tenant={} rejected via engine listener", billId, tenantId);
    }

    /**
     * FIX: backlog 1.1b. ApprovalCompletedEvent only carries the outcome
     * string, not the rejecting approver's own comment — that lives on
     * the individual ApprovalStep. Looks up the request's steps and
     * finds whichever one was actually REJECTED to recover it.
     */
    private String findRejectionReason(TenantId tenantId, UUID billId) {
        return approvalFacade.getLatestRequestForEntity(tenantId, "ap", "BILL", billId)
                .flatMap(r -> r.steps().stream()
                        .filter(s -> "REJECTED".equals(s.status()))
                        .findFirst())
                .map(ApprovalStepResponse::comment)
                .orElse(null);
    }

    private boolean isOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private Optional<ApprovalStepResponse> firstPendingStep(ApprovalRequestResponse r) {
        return r.steps().stream()
                .filter(s -> "PENDING".equals(s.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    /** Same SecurityContextHolder read as ApprovalController's own helper — this module has no other way to know the acting user's roles without depending on identity (see approvals.package-info.java). */
    private List<String> currentUserAuthorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Transactional
    public BillResponse payBill(TenantId tenantId, UUID id, PayBillRequest req, UUID paidBy) {
        ApBill bill = findBill(tenantId, id);
        if (!"APPROVED".equals(bill.getStatus()) && !"OVERDUE".equals(bill.getStatus())) {
            throw new HandyFlowException("Only APPROVED or OVERDUE bills can be paid",
                    HttpStatus.BAD_REQUEST, "BILL_NOT_APPROVED");
        }

        // Post payment journal: debit AP (2010), credit bank account.
        // Same rollback-on-failure reasoning as approveBill() above.
        UUID paymentJournalId = postPaymentJournal(
                tenantId, bill, req.bankAccountId(), req.paymentRef());

        bill.markPaid(paymentJournalId, req.paymentRef(), paidBy);
        billRepo.save(bill);
        log.info("Paid bill={} ref={}", id, req.paymentRef());
        return toBillResponse(bill);
    }

    @Transactional
    public BillResponse cancelBill(TenantId tenantId, UUID id) {
        ApBill bill = findBill(tenantId, id);
        if ("PAID".equals(bill.getStatus())) {
            throw new HandyFlowException("Paid bills cannot be cancelled",
                    HttpStatus.BAD_REQUEST, "BILL_PAID");
        }
        bill.cancel();
        billRepo.save(bill);
        return toBillResponse(bill);
    }

    // ── Evidence upload ───────────────────────────────────────────────────────

    @Transactional
    public BillResponse uploadBillAttachment(TenantId tenantId, UUID id,
                                             UploadEvidenceRequest req) {
        ApBill bill = findBill(tenantId, id);
        bill.uploadAttachment(req.fileBase64(), req.fileName());
        billRepo.save(bill);
        log.info("Uploaded attachment for bill={} file={}", id, req.fileName());
        return toBillResponse(bill);
    }

    @Transactional
    public BillResponse uploadBillPop(TenantId tenantId, UUID id,
                                      UploadEvidenceRequest req, UUID uploadedBy) {
        ApBill bill = findBill(tenantId, id);
        bill.uploadPop(req.fileBase64(), req.fileName(), uploadedBy);
        billRepo.save(bill);
        log.info("Uploaded POP for bill={} file={}", id, req.fileName());
        return toBillResponse(bill);
    }

    @Transactional
    public BatchResponse uploadBatchPop(TenantId tenantId, UUID id,
                                        UploadEvidenceRequest req, UUID uploadedBy) {
        ApEftBatch batch = findBatch(tenantId, id);
        batch.uploadPop(req.fileBase64(), req.fileName(), uploadedBy);
        batchRepo.save(batch);
        return toBatchResponse(batch, null);
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApSummaryResponse getSummary(TenantId tenantId) {
        LocalDate today     = LocalDate.now();
        LocalDate weekEnd   = today.plusDays(7);
        LocalDate monthEnd  = today.plusDays(30);

        BigDecimal outstanding = billRepo.sumOutstanding(tenantId);
        BigDecimal dueWeek     = billRepo.sumDueBetween(tenantId, today, weekEnd);
        BigDecimal dueMonth    = billRepo.sumDueBetween(tenantId, today, monthEnd);

        long draftCount    = billRepo.countByStatus(tenantId, "DRAFT");
        long approvedCount = billRepo.countByStatus(tenantId, "APPROVED");
        long overdueCount  = billRepo.countByStatus(tenantId, "OVERDUE");

        // Count pending batches via JDBC
        long pendingBatches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ap_eft_batches WHERE tenant_id = ? " +
                        "AND status IN ('DRAFT','SUBMITTED')",
                Long.class, tenantId.getValue());

        BigDecimal overdueAmount = billRepo.sumDueBetween(
                tenantId, today.minusYears(10), today.minusDays(1));

        return new ApSummaryResponse(outstanding, overdueAmount, dueWeek, dueMonth,
                draftCount, approvedCount, overdueCount, pendingBatches);
    }

    /**
     * AP aging report — mirrors Accounting's AR aging exactly (same
     * bucket boundaries: CURRENT, 1-30, 31-60, 61-90, 90+ days overdue),
     * sourced from ApBill instead of Invoice. Only APPROVED and OVERDUE
     * bills count, matching sumOutstanding()'s own status filter above —
     * not a new definition of "outstanding" invented for this report.
     */
    @Transactional(readOnly = true)
    public ApAgingReportResponse getAgingReport(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<ApBill> bills = billRepo.findAllOutstanding(tenantId);

        List<ApAgingReportResponse.AgingLine> lines = new ArrayList<>();
        BigDecimal current = BigDecimal.ZERO, d1to30 = BigDecimal.ZERO, d31to60 = BigDecimal.ZERO,
                d61to90 = BigDecimal.ZERO, over90 = BigDecimal.ZERO;

        for (ApBill b : bills) {
            long daysOverdue = ChronoUnit.DAYS.between(b.getDueDate(), today);
            String bucket;
            if (daysOverdue <= 0)       { bucket = "CURRENT"; current = current.add(b.getTotalAmount()); }
            else if (daysOverdue <= 30) { bucket = "1-30";    d1to30  = d1to30.add(b.getTotalAmount()); }
            else if (daysOverdue <= 60) { bucket = "31-60";   d31to60 = d31to60.add(b.getTotalAmount()); }
            else if (daysOverdue <= 90) { bucket = "61-90";   d61to90 = d61to90.add(b.getTotalAmount()); }
            else                        { bucket = "90+";     over90  = over90.add(b.getTotalAmount()); }

            lines.add(new ApAgingReportResponse.AgingLine(b.getId(), b.getBillNumber(), b.getSupplierName(),
                    b.getDueDate(), (int) Math.max(daysOverdue, 0), b.getTotalAmount(), bucket));
        }

        BigDecimal total = current.add(d1to30).add(d31to60).add(d61to90).add(over90);
        return new ApAgingReportResponse(today, lines, current, d1to30, d31to60, d61to90, over90, total);
    }

    // ── EFT Batches ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BatchResponse> getBatches(TenantId tenantId, Pageable pageable) {
        return batchRepo.findAll(tenantId, pageable)
                .map(b -> toBatchResponse(b, null));
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(TenantId tenantId, UUID id) {
        ApEftBatch batch = findBatch(tenantId, id);
        List<BillResponse> bills = batchItemRepo.findByBatchId(id).stream()
                .map(item -> billRepo.findById(item.getBillId())
                        .map(this::toBillResponse).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return toBatchResponse(batch, bills);
    }

    @Transactional
    public BatchResponse createBatch(TenantId tenantId, UUID createdBy, CreateBatchRequest req) {
        // Generate batch number: EFT-0001, EFT-0002, ...
        int seq = batchRepo.findMaxBatchSequence(tenantId) + 1;
        String batchNumber = "EFT-%04d".formatted(seq);

        ApEftBatch batch = ApEftBatch.create(tenantId, batchNumber,
                req.bankAccountId(), req.description(), req.paymentDate(), createdBy);
        batchRepo.save(batch);

        // Add bills to batch
        for (UUID billId : req.billIds()) {
            ApBill bill = billRepo.findByIdAndTenantId(billId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill", billId.toString()));

            if (!"APPROVED".equals(bill.getStatus()) && !"OVERDUE".equals(bill.getStatus())) {
                throw new HandyFlowException(
                        "Bill " + bill.getBillNumber() + " is not approved",
                        HttpStatus.BAD_REQUEST, "BILL_NOT_APPROVED");
            }
            if (batchItemRepo.existsByBillId(billId)) {
                throw new HandyFlowException(
                        "Bill " + bill.getBillNumber() + " is already in another batch",
                        HttpStatus.BAD_REQUEST, "BILL_IN_BATCH");
            }

            ApBatchItem item = ApBatchItem.of(batch.getId(), billId, bill.getTotalAmount());
            batchItemRepo.save(item);
            batch.addBill(bill.getTotalAmount());
            bill.assignToBatch(batch.getId());
            billRepo.save(bill);
        }

        batchRepo.save(batch);
        log.info("Created EFT batch={} bills={} total={}",
                batchNumber, req.billIds().size(), batch.getTotalAmount());
        return toBatchResponse(batch, null);
    }

    @Transactional
    public BatchResponse submitBatch(TenantId tenantId, UUID id) {
        ApEftBatch batch = findBatch(tenantId, id);
        batch.submit();
        batchRepo.save(batch);

        // Notify whoever captured each bill in this batch — previously
        // the person who requested a payment had no way to know their
        // bill's batch had actually gone to the bank, short of manually
        // checking. Deduped so someone who created multiple bills in the
        // same batch gets one notification, not one per bill.
        notifyBatchSubmitted(tenantId, batch);

        return toBatchResponse(batch, null);
    }

    @Transactional
    public BatchResponse confirmBatchPaid(TenantId tenantId, UUID id,
                                          ConfirmBatchPaidRequest req, UUID paidBy) {
        ApEftBatch batch = findBatch(tenantId, id);

        // Mark each bill as paid
        List<ApBatchItem> items = batchItemRepo.findByBatchId(id);
        for (ApBatchItem item : items) {
            billRepo.findByIdAndTenantId(item.getBillId(), tenantId).ifPresent(bill -> {
                UUID payJournalId = postPaymentJournal(
                        tenantId, bill, batch.getBankAccountId(), req.paymentRef());
                bill.markPaid(payJournalId, req.paymentRef(), paidBy);
                billRepo.save(bill);
            });
        }

        batch.confirmPaid(req.paymentRef(), paidBy);
        batchRepo.save(batch);
        log.info("Confirmed batch={} paid ref={}", id, req.paymentRef());
        return toBatchResponse(batch, null);
    }

    @Transactional
    public BatchResponse cancelBatch(TenantId tenantId, UUID id) {
        ApEftBatch batch = findBatch(tenantId, id);
        if ("PAID".equals(batch.getStatus())) {
            throw new HandyFlowException("Paid batches cannot be cancelled",
                    HttpStatus.BAD_REQUEST, "BATCH_PAID");
        }
        // Release bills from batch
        batchItemRepo.findByBatchId(id).forEach(item ->
                billRepo.findByIdAndTenantId(item.getBillId(), tenantId)
                        .ifPresent(bill -> { bill.removeFromBatch(); billRepo.save(bill); }));
        batch.cancel();
        batchRepo.save(batch);
        return toBatchResponse(batch, null);
    }

    /** Export batch as CSV for bank EFT upload. */
    @Transactional(readOnly = true)
    public String exportBatchCsv(TenantId tenantId, UUID id) {
        ApEftBatch batch = findBatch(tenantId, id);
        List<ApBatchItem> items = batchItemRepo.findByBatchId(id);

        StringBuilder csv = new StringBuilder();
        csv.append("Supplier Name,Account Number,Branch Code,Amount,Reference\n");

        for (ApBatchItem item : items) {
            billRepo.findByIdAndTenantId(item.getBillId(), tenantId).ifPresent(bill -> {
                String[] banking = fetchSupplierBanking(tenantId, bill.getSupplierName());
                csv.append(escapeCsv(bill.getSupplierName())).append(",")
                        .append(escapeCsv(banking[0])).append(",")   // account number
                        .append(escapeCsv(banking[1])).append(",")   // branch code
                        .append(item.getAmount()).append(",")
                        .append(escapeCsv(batch.getBatchNumber() + "-" + bill.getBillNumber()))
                        .append("\n");
            });
        }
        return csv.toString();
    }

    // ── Scheduler: mark overdue ───────────────────────────────────────────────

    @Transactional
    public void markOverdueBills() {
        List<ApBill> overdue = billRepo.findAllOverdueAcrossTenants(LocalDate.now());
        overdue.forEach(bill -> {
            bill.markOverdue();
            billRepo.save(bill);
        });
        if (!overdue.isEmpty()) {
            log.info("Marked {} bills as OVERDUE", overdue.size());
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private void notifyBillPendingApproval(TenantId tenantId, ApBill bill) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.BILL_PENDING_APPROVAL)
                .title("Bill awaiting approval: " + bill.getSupplierName())
                .message(bill.getSupplierName() + " — " + bill.getBillNumber()
                        + " (R " + bill.getTotalAmount() + ") is waiting for approval.")
                .actionUrl("/ap/bills/" + bill.getId())
                .sourceModule("ap")
                .sourceEntityId(bill.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyBatchSubmitted(TenantId tenantId, ApEftBatch batch) {
        List<ApBatchItem> items = batchItemRepo.findByBatchId(batch.getId());
        // Distinct createdBy across all bills in the batch — one
        // notification per person, not one per bill.
        Set<UUID> notified = new HashSet<>();
        for (ApBatchItem item : items) {
            billRepo.findById(item.getBillId()).ifPresent(bill -> {
                UUID createdBy = bill.getCreatedBy();
                if (createdBy == null || !notified.add(createdBy)) return;

                String email = fetchUserEmail(createdBy);
                if (email == null || email.isBlank()) return;
                String name = fetchUserName(createdBy);

                notificationService.send(NotificationRequest.builder()
                        .tenantId(tenantId)
                        .type(NotificationType.EFT_BATCH_SUBMITTED)
                        .title("Batch submitted: " + batch.getBatchNumber())
                        .message("Your bill " + bill.getBillNumber() + " is included in EFT batch "
                                + batch.getBatchNumber() + ", now submitted to the bank.")
                        .actionUrl("/ap/batches/" + batch.getId())
                        .sourceModule("ap")
                        .sourceEntityId(batch.getId().toString())
                        .recipient(Recipient.user(createdBy, name, email, null))
                        .build());
            });
        }
    }

    // Deliberate duplicates of the identically-named helpers already used
    // in the recruiter module (RecruiterService/InterviewReminderScheduler)
    // rather than a cross-module dependency between AP and Recruiter for
    // three lines of JDBC — same "duplication costs less than coupling"
    // tradeoff made repeatedly elsewhere this session.
    private String fetchUserName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) { return null; }
    }

    private String fetchUserEmail(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT email FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) { return null; }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ApBill findBill(TenantId tenantId, UUID id) {
        return billRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", id.toString()));
    }

    private ApEftBatch findBatch(TenantId tenantId, UUID id) {
        return batchRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("EFT Batch", id.toString()));
    }

    private UUID postApprovalJournal(TenantId tenantId, ApBill bill, String expenseCode) {
        UUID expenseAccountId = findAccountByCode(tenantId, expenseCode);
        if (expenseAccountId == null) {
            throw new HandyFlowException(
                    "Chart of Accounts is missing expense account " + expenseCode
                            + " for category '" + bill.getCategory() + "' — cannot post approval journal",
                    HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_SEEDED");
        }
        UUID apAccountId = findAccountByCode(tenantId, "2010");
        if (apAccountId == null) {
            throw new HandyFlowException(
                    "Chart of Accounts is missing the Accounts Payable account (2010) — cannot post approval journal",
                    HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_SEEDED");
        }

        boolean hasVat = bill.getVatAmount() != null && bill.getVatAmount().compareTo(BigDecimal.ZERO) > 0;
        UUID vatAccountId = null;
        if (hasVat) {
            vatAccountId = findAccountByCode(tenantId, VAT_INPUT_ACCOUNT_CODE);
            if (vatAccountId == null) {
                throw new HandyFlowException(
                        "Chart of Accounts is missing the VAT Input account (" + VAT_INPUT_ACCOUNT_CODE
                                + ") — cannot post a VAT-inclusive bill without it",
                        HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_SEEDED");
            }
        }

        List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
        lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                expenseAccountId, bill.getCategory() + " expense — " + bill.getSupplierName(),
                bill.getAmount(), null));
        if (hasVat) {
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    vatAccountId, "VAT input — " + bill.getSupplierName(),
                    bill.getVatAmount(), null));
        }
        lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                apAccountId, "Accounts payable — " + bill.getSupplierName(),
                null, bill.getTotalAmount()));

        CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                bill.getBillDate(),
                "AP bill approved: " + bill.getBillNumber() + " — " + bill.getSupplierName(),
                bill.getBillNumber(), "MANUAL", lines);

        JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
        JournalEntryResponse posted  = accountingFacade.postJournalEntry(tenantId, created.id());
        return posted.id();
    }

    private UUID postPaymentJournal(TenantId tenantId, ApBill bill,
                                    UUID bankAccountId, String ref) {
        if (bankAccountId == null) {
            throw new HandyFlowException(
                    "A bank account is required to record this payment — cannot post a balanced payment journal without one",
                    HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_REQUIRED");
        }
        UUID bankAccountGL = findBankAccountGL(bankAccountId);
        if (bankAccountGL == null) {
            throw new HandyFlowException(
                    "Bank account " + bankAccountId + " does not resolve to a Chart of Accounts entry — cannot post a balanced payment journal",
                    HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_NOT_LINKED");
        }
        UUID apAccountId = findAccountByCode(tenantId, "2010");
        if (apAccountId == null) {
            throw new HandyFlowException(
                    "Chart of Accounts is missing the Accounts Payable account (2010) — cannot post payment journal",
                    HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_SEEDED");
        }

        List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                new CreateJournalEntryRequest.JournalLineRequest(
                        apAccountId, "Payment to " + bill.getSupplierName(), bill.getTotalAmount(), null),
                new CreateJournalEntryRequest.JournalLineRequest(
                        bankAccountGL, "Payment ref: " + ref, null, bill.getTotalAmount()));

        CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                LocalDate.now(), "Payment: " + bill.getBillNumber() + " ref:" + ref,
                ref, "PAYMENT", lines);

        JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
        JournalEntryResponse posted  = accountingFacade.postJournalEntry(tenantId, created.id());
        return posted.id();
    }

    private UUID findAccountByCode(TenantId tenantId, String code) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code = ? AND active = true LIMIT 1",
                    UUID.class, tenantId.getValue(), code);
        } catch (Exception e) { return null; }
    }

    private UUID findBankAccountGL(UUID bankAccountId) {
        if (bankAccountId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT account_id FROM acc_bank_accounts WHERE id = ?",
                    UUID.class, bankAccountId);
        } catch (Exception e) { return null; }
    }

    private String[] fetchSupplierBanking(TenantId tenantId, String supplierName) {
        if (supplierName == null || supplierName.isBlank()) return new String[]{"", ""};
        return supplierBankingRepo.findByTenantIdAndSupplierName(tenantId, supplierName)
                .map(b -> new String[]{b.getAccountNumber(), b.getBranchCode()})
                .orElse(new String[]{"", ""});
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private BillResponse toBillResponse(ApBill b) {
        return toBillResponse(b, null);
    }

    private BillResponse toBillResponse(ApBill b, String possibleDuplicateWarning) {
        return new BillResponse(
                b.getId(), b.getSupplierId(), b.getSupplierName(),
                b.getBillNumber(), b.getBillDate(), b.getDueDate(),
                b.getCategory(), b.getDescription(),
                b.getAmount(), b.getVatAmount(), b.getTotalAmount(), b.getCurrency(),
                b.getStatus(), b.isOverdue(), b.daysUntilDue(),
                b.getAttachmentUrl() != null, b.getPopUrl() != null,
                b.getPaymentRef(), b.getBatchId(), b.getNotes(),
                b.getJournalEntryId(), b.getPaymentJournalId(),
                b.getFirstApprovedBy(), b.getFirstApprovedAt(),
                b.getPaidAt(), b.getCreatedAt(), possibleDuplicateWarning,
                b.getRejectionReason());
    }

    private BatchResponse toBatchResponse(ApEftBatch b, List<BillResponse> bills) {
        String bankName = b.getBankAccountId() != null
                ? fetchBankAccountName(b.getBankAccountId()) : null;
        return new BatchResponse(
                b.getId(), b.getBatchNumber(), b.getBankAccountId(), bankName,
                b.getDescription(), b.getTotalAmount(), b.getBillCount(),
                b.getStatus(), b.getPaymentDate(), b.getPaymentRef(),
                b.getPopUrl() != null, bills,
                b.getSubmittedAt(), b.getPaidAt(), b.getCreatedAt());
    }

    private String fetchBankAccountName(UUID bankAccountId) {
        try {
            return jdbc.queryForObject(
                    "SELECT account_name FROM acc_bank_accounts WHERE id = ?",
                    String.class, bankAccountId);
        } catch (Exception e) { return null; }
    }
}