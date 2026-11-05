package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * INV-01 repair. Before patch 0647, invoices from recurring schedules, the scheduler and quote conversion were issued
 * without an issuance journal. Steps: preview (nothing changes), approve (a person signs off the exact list, with a
 * reason), run (a different person where one exists). Each journal uses the normal posting key, so an invoice that already
 * has its journal is never posted twice; locked periods are never bypassed; every posting is written to the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceLedgerRepairService {

    private static final String AR = "1100", REVENUE = "4000", VAT_OUTPUT = "2100";
    static final String PERMISSION = "ACCOUNTING_ADMIN";

    private final JdbcTemplate jdbc;
    private final AccAccountRepository accountRepo;
    private final PeriodLockService periodLock;
    private final LedgerPostingService posting;
    private final SeparationOfDutiesChecker duties;
    private final AuditTrailFacade auditTrail;

    public record Item(UUID invoiceId, String invoiceNumber, String status, LocalDate issuedOn, BigDecimal subtotal, BigDecimal vatTotal,
                       BigDecimal total, String category, LocalDate willBePostedOn, boolean queuedAsProblem) {}

    public record Counts(int originalDate, int priorPeriodCorrection, int unpostable) {}

    public record Preview(LocalDate booksLockedThrough, int count, BigDecimal total, Counts counts, List<Item> items) {}

    public record Approval(UUID id, String previewHash, int count, Counts counts, Instant expiresAt) {}

    public record Result(int posted, int skipped, List<String> notes) {}

    @Transactional(readOnly = true)
    public Preview preview(TenantId tenantId) {
        LocalDate locked = periodLock.lockedThrough(tenantId), today = LocalDate.now();
        List<Item> items = new ArrayList<>();
        int o = 0, p = 0, u = 0;
        for (Missing m : missing(tenantId)) {
            String cat = InvoiceLedgerRepairRules.category(m.issuedOn, today, locked);
            items.add(new Item(m.id, m.number, m.status, m.issuedOn, m.subtotal, m.vat, m.total, cat,
                    InvoiceLedgerRepairRules.entryDate(cat, m.issuedOn, today), m.queued));
            switch (cat) { case InvoiceLedgerRepairRules.ORIGINAL_DATE -> o++; case InvoiceLedgerRepairRules.PRIOR_PERIOD_CORRECTION -> p++; default -> u++; }
        }
        BigDecimal total = items.stream().map(Item::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(locked, items.size(), total, new Counts(o, p, u), items);
    }

    /** A person signs off the previewed list. The approval is for exactly that list and lasts 24 hours. */
    @Transactional
    public Approval approve(TenantId tenantId, String reason, UUID by) {
        String problem = InvoiceLedgerRepairRules.reasonProblem(reason);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        if (by == null) throw new HandyFlowException("Your user could not be identified.", HttpStatus.FORBIDDEN, "NO_USER");
        List<Missing> list = missing(tenantId);
        if (list.isEmpty()) throw new HandyFlowException("There is nothing to repair.", HttpStatus.CONFLICT, "NOTHING_TO_REPAIR");
        Preview pv = preview(tenantId);
        String hash = hash(list);
        UUID id = UUID.randomUUID();
        Instant expires = Instant.now().plus(java.time.Duration.ofHours(24));
        jdbc.update("""
                INSERT INTO invoice_ledger_repair_approvals (id, tenant_id, preview_hash, invoice_count, total_amount, original_dated, prior_period,
                                                             unpostable, reason, approved_by, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), hash, pv.count(), pv.total(), pv.counts().originalDate(), pv.counts().priorPeriodCorrection(),
                pv.counts().unpostable(), reason.trim(), by, java.sql.Timestamp.from(expires));
        auditTrail.record(tenantId, "INVOICE_LEDGER_REPAIR_APPROVED", "INVOICE_LEDGER_REPAIR", id, null,
                Map.of("reason", reason.trim(), "invoices", pv.count(), "total", pv.total().toPlainString(), "originalDate", pv.counts().originalDate(),
                        "priorPeriodCorrection", pv.counts().priorPeriodCorrection(), "unpostable", pv.counts().unpostable()));
        return new Approval(id, hash, pv.count(), pv.counts(), expires);
    }

    /** Posts the approved list. Needs a person other than the approver where one exists. */
    @Transactional
    public Result repair(TenantId tenantId, UUID approvalId, UUID by) {
        Map<String, Object> a = jdbc.queryForList("""
                SELECT preview_hash, approved_by, expires_at, executed_at, reason FROM invoice_ledger_repair_approvals
                 WHERE id = ? AND tenant_id = ? FOR UPDATE""", approvalId, tenantId.getValue()).stream().findFirst()
                .orElseThrow(() -> new HandyFlowException("That approval does not exist.", HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND"));
        if (a.get("executed_at") != null) throw new HandyFlowException("This approval has already been used.", HttpStatus.CONFLICT, "APPROVAL_USED");
        if (((java.sql.Timestamp) a.get("expires_at")).toInstant().isBefore(Instant.now()))
            throw new HandyFlowException("This approval has expired. Preview and approve again.", HttpStatus.CONFLICT, "APPROVAL_EXPIRED");
        List<Missing> list = missing(tenantId);
        if (!hash(list).equals(a.get("preview_hash")))
            throw new HandyFlowException("The list of invoices has changed since it was approved. Preview and approve again.", HttpStatus.CONFLICT, "APPROVAL_STALE");
        UUID approver = (UUID) a.get("approved_by");
        duties.require(tenantId, PERMISSION, approver, by, "The person who approved the repair cannot also run it. Ask another accounting administrator.",
                "INVOICE_LEDGER_REPAIR", approvalId, "ran the repair they approved");

        UUID ar = account(tenantId, AR), revenue = account(tenantId, REVENUE), vat = account(tenantId, VAT_OUTPUT);
        if (ar == null || revenue == null)
            throw new HandyFlowException("The chart of accounts has no Accounts Receivable (" + AR + ") or Revenue (" + REVENUE + ") account.", HttpStatus.CONFLICT, "NO_ACCOUNTS");
        LocalDate locked = periodLock.lockedThrough(tenantId), today = LocalDate.now();
        Instant repairedAt = Instant.now();
        String reason = String.valueOf(a.get("reason"));
        int posted = 0, skipped = 0;
        List<String> notes = new ArrayList<>();
        for (Missing m : list) {
            String cat = InvoiceLedgerRepairRules.category(m.issuedOn, today, locked);
            LocalDate date = InvoiceLedgerRepairRules.entryDate(cat, m.issuedOn, today);
            String why = date == null ? "The books are locked through " + locked + ", so nothing can be posted today." : null;
            if (why == null && m.vat.signum() > 0 && vat == null) why = "The chart of accounts has no VAT Output (" + VAT_OUTPUT + ") account.";
            if (why != null) { skipped++; notes.add(m.number + ": " + why); continue; }
            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(ar, "Invoice issued — " + m.number, m.total, null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(revenue, "Revenue — " + m.number, null, m.subtotal));
            if (m.vat.signum() > 0) lines.add(new CreateJournalEntryRequest.JournalLineRequest(vat, "VAT output — " + m.number, null, m.vat));
            String note = "Invoice issued: " + m.number + " — INV-01 repair" + (InvoiceLedgerRepairRules.PRIOR_PERIOD_CORRECTION.equals(cat)
                    ? " (prior-period correction, invoice dated " + m.issuedOn + ")" : "");
            try {
                posting.postOnceOrRecord(tenantId, "invoicing:invoice-issued:" + m.id,
                        new CreateJournalEntryRequest(date, note, m.number, InvoiceLedgerRepairRules.entryType(cat), lines));
                posted++;
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("invoiceNumber", m.number);
                after.put("originalInvoiceDate", String.valueOf(m.issuedOn));
                after.put("journalDate", date.toString());
                after.put("classification", cat);
                after.put("sourceKey", "invoicing:invoice-issued:" + m.id);
                after.put("approvalId", approvalId.toString());
                after.put("reason", reason);
                after.put("repairedAt", repairedAt.toString());
                auditTrail.record(tenantId, "INVOICE_LEDGER_REPAIRED", "INVOICE", m.id, null, after);
            } catch (RuntimeException e) {
                skipped++;
                notes.add(m.number + ": " + e.getMessage());
            }
        }
        jdbc.update("UPDATE invoice_ledger_repair_approvals SET executed_by = ?, executed_at = now(), posted_count = ? WHERE id = ?", by, posted, approvalId);
        log.warn("[Accounting] INV-01 repair tenant={} approval={} posted={} skipped={} by={}", tenantId, approvalId, posted, skipped, by);
        return new Result(posted, skipped, notes);
    }

    private record Missing(UUID id, String number, String status, LocalDate issuedOn, BigDecimal subtotal, BigDecimal vat, BigDecimal total, boolean queued) {}

    private static String hash(List<Missing> list) {
        return InvoiceLedgerRepairRules.previewHash(list.stream().map(Missing::id).toList(), list.stream().map(Missing::total).toList());
    }

    private List<Missing> missing(TenantId tenantId) {
        return jdbc.query("""
                SELECT i.id, i.invoice_number, i.status, i.issued_at, i.subtotal, i.vat_total, i.total,
                       EXISTS (SELECT 1 FROM acc_posting_failures f WHERE f.tenant_id = i.tenant_id AND f.status = 'OPEN'
                                  AND f.source_key = 'invoicing:invoice-issued:' || i.id) AS queued
                  FROM invoices i
                 WHERE i.tenant_id = ? AND i.status NOT IN ('DRAFT', 'CANCELLED') AND i.deleted_at IS NULL
                   AND NOT EXISTS (SELECT 1 FROM acc_journal_entries j WHERE j.tenant_id = i.tenant_id AND j.deleted_at IS NULL
                                      AND j.source_key = 'invoicing:invoice-issued:' || i.id)
                 ORDER BY i.issued_at, i.invoice_number, i.id""",
                (rs, n) -> new Missing(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime().toLocalDate(),
                        nz(rs.getBigDecimal(5)), nz(rs.getBigDecimal(6)), nz(rs.getBigDecimal(7)), rs.getBoolean(8)),
                tenantId.getValue());
    }

    private UUID account(TenantId tenantId, String code) {
        return accountRepo.findAllActive(tenantId).stream().filter(a -> code.equals(a.getAccountCode())).map(a -> a.getId()).findFirst().orElse(null);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
