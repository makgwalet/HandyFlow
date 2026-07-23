package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.*;
import za.co.handyflow.platform.ap.domain.repository.*;
import za.co.handyflow.platform.ap.dto.*;
import za.co.handyflow.platform.shared.*;
// Same shared notification infra used by the recruiter module —
// NotificationService/TenantAdminRecipients live outside AP's ownership
// but are explicitly documented as the single cross-module entry point
// every module uses (see NotificationService's own class Javadoc).
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

    // Maker-checker: bills above this amount require a second approval
    // from a DIFFERENT person before the journal actually posts. Below
    // it, the existing single-step approve() flow is unchanged.
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

        // Notify whoever can approve bills — previously nothing told
        // anyone a new bill was waiting; it would only be noticed by
        // manually checking the Draft list. Reusing TenantAdminRecipients
        // (same tradeoff already made in the recruiter module: notifies
        // all tenant admins, not specifically AP_MANAGE holders — there's
        // no narrower "resolve everyone with permission X" port available).
        notifyBillPendingApproval(tenantId, bill);

        return toBillResponse(bill, duplicateWarning);
    }

    @Transactional
    public BillResponse updateBill(TenantId tenantId, UUID id, UpdateBillRequest req) {
        ApBill bill = findBill(tenantId, id);
        if (!"DRAFT".equals(bill.getStatus())) {
            throw new HandyFlowException("Only DRAFT bills can be edited",
                    HttpStatus.BAD_REQUEST, "BILL_NOT_DRAFT");
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

    @Transactional
    public BillResponse approveBill(TenantId tenantId, UUID id, UUID approvedBy) {
        ApBill bill = findBill(tenantId, id);

        boolean aboveThreshold = bill.getTotalAmount().compareTo(secondApprovalThreshold) > 0;

        if (aboveThreshold && "SECOND_APPROVAL".equals(bill.getStatus())) {
            // Second approval — must be a different person from whoever
            // gave the first one, or the whole control is meaningless.
            if (approvedBy != null && approvedBy.equals(bill.getFirstApprovedBy())) {
                throw new HandyFlowException(
                        "This bill already has your first approval — a different person must give the second approval",
                        HttpStatus.BAD_REQUEST, "SAME_APPROVER");
            }
            String expenseAccount = CATEGORY_ACCOUNT.getOrDefault(bill.getCategory(), "5100");
            UUID journalId = postApprovalJournal(tenantId, bill, expenseAccount);
            bill.approve(journalId);
            billRepo.save(bill);
            log.info("Bill={} received second approval from={} journal={}", id, approvedBy, journalId);
            return toBillResponse(bill);
        }

        if (aboveThreshold) {
            // First approval on a high-value bill — no journal yet.
            // Deliberately does not fall through to postApprovalJournal();
            // that only happens once a second, different person approves.
            bill.requestSecondApproval(approvedBy);
            billRepo.save(bill);
            log.info("Bill={} (R {}) exceeds second-approval threshold (R {}) — first approval from={}, awaiting second",
                    id, bill.getTotalAmount(), secondApprovalThreshold, approvedBy);
            // Same notification used for a brand-new pending bill — the
            // phrasing ("is waiting for approval") already reads correctly
            // for "still needs one more approval" too, so this reuses it
            // rather than adding a near-duplicate message for a narrower case.
            notifyBillPendingApproval(tenantId, bill);
            return toBillResponse(bill);
        }

        // Below threshold — original single-step flow, unchanged. Post
        // journal entry: debit expense account, debit VAT input (if any),
        // credit AP (2010). If this throws — bad Chart of Accounts setup,
        // DB error, whatever — the whole @Transactional method rolls
        // back: bill stays un-approved, no partial journal rows survive.
        // Previously postApprovalJournal() swallowed every exception and
        // returned null, and the bill got marked APPROVED anyway with a
        // null journal reference — a bill could be "approved" with zero
        // accounting trail and nothing would ever tell you.
        String expenseAccount = CATEGORY_ACCOUNT.getOrDefault(bill.getCategory(), "5100");
        UUID journalId = postApprovalJournal(tenantId, bill, expenseAccount);

        bill.approve(journalId);
        billRepo.save(bill);
        log.info("Approved bill={} journal={}", id, journalId);
        return toBillResponse(bill);
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
                // FIXED: was fetchSupplierBanking(bill.getSupplierId()) —
                // supplierId is essentially never populated across this
                // whole system (every bill checked this session has it
                // null), so that call always hit its own null-guard and
                // NEVER actually reached the buggy customers-table query
                // beneath it. Now looks up the real ap_supplier_banking
                // table by supplier NAME instead — matching the same
                // pattern generateSupplierStatement() already uses.
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

    // FIXED, all four items from the original audit: entry numbering now
    // comes from the real JournalNumberGenerator (via AccountingFacade ->
    // AccountingService.createJournalEntry(), not System.currentTimeMillis()
    // and not the custom ap_journal_number_counters table built earlier
    // this session — that table is now dead code, see note below); this
    // routes through AccountingFacade instead of raw JDBC, so it gets
    // real validation (balance check, minimum lines, positive amounts)
    // for free instead of reimplementing it; VAT is split onto its own
    // line against the VAT Input account; and missing/unseeded accounts
    // throw a clear, specific error instead of silently returning null.
    // Journal entries are created as DRAFT then immediately posted in the
    // same call — still no human review step between the two, since
    // nothing in this session's scope asked for one, but at least both
    // steps now go through AccountingService's real DRAFT->POSTED
    // lifecycle instead of an INSERT hardcoded straight to 'POSTED'.
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
        // Debit expense account — VAT-EXCLUSIVE amount only, so the VAT
        // portion shows up separately as claimable Input VAT rather than
        // being buried inside the expense line.
        lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                expenseAccountId, bill.getCategory() + " expense — " + bill.getSupplierName(),
                bill.getAmount(), null));
        // Debit VAT Input (Claimable) — only when the bill actually has VAT.
        if (hasVat) {
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    vatAccountId, "VAT input — " + bill.getSupplierName(),
                    bill.getVatAmount(), null));
        }
        // Credit AP — full total (amount + VAT). Journal stays balanced:
        // expense + VAT (debits) == AP (credit) == totalAmount.
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

    // Same fixes as postApprovalJournal() above, applied to payments. A
    // payment now REQUIRES a resolvable bank account — per explicit
    // decision earlier this session, since silently skipping the credit
    // line when bankAccountGL couldn't be resolved used to leave a
    // debit-only, UNBALANCED journal posted with nothing catching it.
    // AccountingService.createJournalEntry()'s own balance check is a
    // second, independent line of defense against that now too.
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

    // nextJournalNumber() REMOVED — it's now genuinely dead code. Entry
    // numbering happens inside AccountingService.createJournalEntry() via
    // the real JournalNumberGenerator, so this custom per-tenant counter
    // is no longer called from anywhere. The ap_journal_number_counters
    // table (and its migration) are now unused — not dropped here, since
    // dropping a table is a more consequential decision than adding one;
    // left for you to remove deliberately if you want the cleanup.

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

    // FIXED — was querying `customers` (the CRM/AR table, i.e. people who
    // owe THIS business money) by supplierId, which is essentially always
    // null on a bill (an AP supplier, someone THIS business owes money
    // to, was never linked to any real entity). That old query almost
    // certainly never ran in practice — the null-guard above it caught
    // every real call first. Now looks up the real ap_supplier_banking
    // table by supplier NAME (case-insensitive), the only identifier
    // that's actually populated consistently across this whole module.
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

    // possibleDuplicateWarning is only ever non-null from createBill()'s
    // own call site above — every other caller uses the 1-arg overload,
    // which always passes null, so get/list responses never carry a
    // duplicate warning (that would be meaningless outside of creation).
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
                b.getPaidAt(), b.getCreatedAt(), possibleDuplicateWarning);
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