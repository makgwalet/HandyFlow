package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * INV-01 repair. Before patch 0647 invoices from recurring schedules, the scheduler and quote conversion were issued
 * without an issuance journal. This lists them (preview) and posts the missing journals when an accountant asks, under the
 * same key the normal posting uses, so an invoice that already has its journal is never posted twice. The date policy is
 * always chosen by the person: ORIGINAL (the day the invoice was issued, refused where the books are locked) or TODAY.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceLedgerRepairService {

    private static final String AR = "1100", REVENUE = "4000", VAT_OUTPUT = "2100";

    private final JdbcTemplate jdbc;
    private final AccAccountRepository accountRepo;
    private final PeriodLockService periodLock;
    private final LedgerPostingService posting;

    public record Item(UUID invoiceId, String invoiceNumber, String status, LocalDate issuedOn, BigDecimal subtotal,
                       BigDecimal vatTotal, BigDecimal total, boolean originalDateOpen, boolean queuedAsProblem) {}

    public record Preview(LocalDate booksLockedThrough, int count, BigDecimal total, List<Item> items) {}

    public record Result(String policy, int posted, int skipped, List<String> notes) {}

    public Preview preview(TenantId tenantId) {
        LocalDate locked = periodLock.lockedThrough(tenantId);
        List<Item> items = missing(tenantId).stream().map(m -> new Item(m.id, m.number, m.status, m.issuedOn, m.subtotal, m.vat, m.total,
                InvoiceLedgerRepairRules.skipReason(InvoiceLedgerRepairRules.entryDate(InvoiceLedgerRepairRules.ORIGINAL, m.issuedOn, LocalDate.now()), locked) == null,
                m.queued)).toList();
        BigDecimal total = items.stream().map(Item::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(locked, items.size(), total, items);
    }

    /** @param invoiceIds the invoices to repair; null or empty means every one that is missing */
    public Result repair(TenantId tenantId, String policy, Set<UUID> invoiceIds) {
        if (!InvoiceLedgerRepairRules.validPolicy(policy))
            throw new HandyFlowException("Choose the date policy: ORIGINAL or TODAY.", org.springframework.http.HttpStatus.BAD_REQUEST, "POLICY_REQUIRED");
        UUID ar = account(tenantId, AR), revenue = account(tenantId, REVENUE), vat = account(tenantId, VAT_OUTPUT);
        if (ar == null || revenue == null)
            throw new HandyFlowException("The chart of accounts has no Accounts Receivable (" + AR + ") or Revenue (" + REVENUE + ") account.",
                    org.springframework.http.HttpStatus.CONFLICT, "NO_ACCOUNTS");
        LocalDate locked = periodLock.lockedThrough(tenantId), today = LocalDate.now();
        int posted = 0, skipped = 0;
        List<String> notes = new ArrayList<>();
        for (Missing m : missing(tenantId)) {
            if (invoiceIds != null && !invoiceIds.isEmpty() && !invoiceIds.contains(m.id)) continue;
            LocalDate date = InvoiceLedgerRepairRules.entryDate(policy, m.issuedOn, today);
            String why = InvoiceLedgerRepairRules.skipReason(date, locked);
            if (why == null && m.vat.signum() > 0 && vat == null) why = "The chart of accounts has no VAT Output (" + VAT_OUTPUT + ") account.";
            if (why != null) { skipped++; notes.add(m.number + ": " + why); continue; }
            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(ar, "Invoice issued — " + m.number, m.total, null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(revenue, "Revenue — " + m.number, null, m.subtotal));
            if (m.vat.signum() > 0) lines.add(new CreateJournalEntryRequest.JournalLineRequest(vat, "VAT output — " + m.number, null, m.vat));
            String note = "Invoice issued: " + m.number + " (posted late, INV-01 repair" + (date.equals(m.issuedOn) ? "" : "; issued " + m.issuedOn) + ")";
            try {
                posting.postOnceOrRecord(tenantId, "invoicing:invoice-issued:" + m.id,
                        new CreateJournalEntryRequest(date, note, m.number, "MANUAL", lines));
                posted++;
            } catch (RuntimeException e) {
                skipped++;
                notes.add(m.number + ": " + e.getMessage());
            }
        }
        log.warn("[Accounting] INV-01 repair tenant={} policy={} posted={} skipped={}", tenantId, policy, posted, skipped);
        return new Result(policy, posted, skipped, notes);
    }

    private record Missing(UUID id, String number, String status, LocalDate issuedOn, BigDecimal subtotal, BigDecimal vat, BigDecimal total, boolean queued) {}

    private List<Missing> missing(TenantId tenantId) {
        return jdbc.query("""
                SELECT i.id, i.invoice_number, i.status, i.issued_at, i.subtotal, i.vat_total, i.total,
                       EXISTS (SELECT 1 FROM acc_posting_failures f WHERE f.tenant_id = i.tenant_id AND f.status = 'OPEN'
                                  AND f.source_key = 'invoicing:invoice-issued:' || i.id) AS queued
                  FROM invoices i
                 WHERE i.tenant_id = ? AND i.status NOT IN ('DRAFT', 'CANCELLED') AND i.deleted_at IS NULL
                   AND NOT EXISTS (SELECT 1 FROM acc_journal_entries j WHERE j.tenant_id = i.tenant_id AND j.deleted_at IS NULL
                                      AND j.source_key = 'invoicing:invoice-issued:' || i.id)
                 ORDER BY i.issued_at, i.invoice_number""",
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
