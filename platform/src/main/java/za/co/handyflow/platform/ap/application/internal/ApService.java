package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApService {

    private final ApBillRepository      billRepo;
    private final ApEftBatchRepository  batchRepo;
    private final ApBatchItemRepository batchItemRepo;
    private final JdbcTemplate          jdbc;

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
        return toBillResponse(bill);
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
    public BillResponse approveBill(TenantId tenantId, UUID id) {
        ApBill bill = findBill(tenantId, id);

        // Post journal entry: debit expense account, credit AP (2010)
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

        // Post payment journal: debit AP (2010), credit bank account
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
                // Try to get bank details from CRM customer
                String[] banking = fetchSupplierBanking(bill.getSupplierId());
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
        // WHY JDBC? Cross-module call — AP cannot directly use AccJournalEntry domain.
        // We call the accounting SQL directly to avoid module coupling.
        try {
            UUID journalId = UUID.randomUUID();
            String entryNumber = "AP-" + System.currentTimeMillis();

            // Get account IDs
            UUID expenseAccountId = findAccountByCode(tenantId, expenseCode);
            UUID apAccountId      = findAccountByCode(tenantId, "2010");

            if (expenseAccountId == null || apAccountId == null) {
                log.warn("Cannot post AP journal — accounts not seeded for tenant={}", tenantId);
                return null;
            }

            jdbc.update("""
                INSERT INTO acc_journal_entries
                (id, tenant_id, entry_number, entry_date, description, entry_type, status,
                 total_debit, total_credit, posted_at, created_at, updated_at)
                VALUES (?,?,?,?,?,'MANUAL','POSTED',?,?,now(),now(),now())
                """,
                journalId, tenantId.getValue(), entryNumber, bill.getBillDate(),
                "AP bill approved: " + bill.getBillNumber() + " — " + bill.getSupplierName(),
                bill.getTotalAmount(), bill.getTotalAmount());

            // Debit expense account
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                VALUES (?,?,?,?,?,?,0,1,now())
                """,
                UUID.randomUUID(), tenantId.getValue(), journalId, expenseAccountId,
                bill.getCategory() + " expense — " + bill.getSupplierName(),
                bill.getTotalAmount());

            // Credit AP
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                VALUES (?,?,?,?,?,0,?,2,now())
                """,
                UUID.randomUUID(), tenantId.getValue(), journalId, apAccountId,
                "Accounts payable — " + bill.getSupplierName(),
                bill.getTotalAmount());

            return journalId;
        } catch (Exception e) {
            log.error("Failed to post AP approval journal for bill={}: {}", bill.getId(), e.getMessage());
            return null;
        }
    }

    private UUID postPaymentJournal(TenantId tenantId, ApBill bill,
                                     UUID bankAccountId, String ref) {
        try {
            UUID journalId    = UUID.randomUUID();
            String entryNumber = "PAY-" + System.currentTimeMillis();

            UUID apAccountId   = findAccountByCode(tenantId, "2010");
            UUID bankAccountGL = findBankAccountGL(bankAccountId);

            if (apAccountId == null) {
                log.warn("Cannot post payment journal — AP account not seeded");
                return null;
            }

            jdbc.update("""
                INSERT INTO acc_journal_entries
                (id, tenant_id, entry_number, entry_date, description, entry_type, status,
                 total_debit, total_credit, posted_at, created_at, updated_at)
                VALUES (?,?,?,?,?,'PAYMENT','POSTED',?,?,now(),now(),now())
                """,
                journalId, tenantId.getValue(), entryNumber, LocalDate.now(),
                "Payment: " + bill.getBillNumber() + " ref:" + ref,
                bill.getTotalAmount(), bill.getTotalAmount());

            // Debit AP
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                VALUES (?,?,?,?,?,?,0,1,now())
                """,
                UUID.randomUUID(), tenantId.getValue(), journalId, apAccountId,
                "Payment to " + bill.getSupplierName(), bill.getTotalAmount());

            // Credit bank
            if (bankAccountGL != null) {
                jdbc.update("""
                    INSERT INTO acc_journal_lines
                    (id, tenant_id, journal_entry_id, account_id, description, debit_amount, credit_amount, sort_order, created_at)
                    VALUES (?,?,?,?,?,0,?,2,now())
                    """,
                    UUID.randomUUID(), tenantId.getValue(), journalId, bankAccountGL,
                    "Payment ref: " + ref, bill.getTotalAmount());
            }

            return journalId;
        } catch (Exception e) {
            log.error("Failed to post payment journal for bill={}: {}", bill.getId(), e.getMessage());
            return null;
        }
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

    private String[] fetchSupplierBanking(UUID supplierId) {
        if (supplierId == null) return new String[]{"", ""};
        try {
            var row = jdbc.queryForMap(
                    "SELECT bank_account, bank_branch FROM customers WHERE id = ?", supplierId);
            return new String[]{
                Objects.toString(row.get("bank_account"), ""),
                Objects.toString(row.get("bank_branch"),  "")
            };
        } catch (Exception e) { return new String[]{"", ""}; }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private BillResponse toBillResponse(ApBill b) {
        return new BillResponse(
                b.getId(), b.getSupplierId(), b.getSupplierName(),
                b.getBillNumber(), b.getBillDate(), b.getDueDate(),
                b.getCategory(), b.getDescription(),
                b.getAmount(), b.getVatAmount(), b.getTotalAmount(), b.getCurrency(),
                b.getStatus(), b.isOverdue(), b.daysUntilDue(),
                b.getAttachmentUrl() != null, b.getPopUrl() != null,
                b.getPaymentRef(), b.getBatchId(), b.getNotes(),
                b.getPaidAt(), b.getCreatedAt());
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
