package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Firm reports: matters, unbilled work, debtors ageing, billing and collections, trust balances. Every report leaves out the matters and clients the caller is
 * walled from, and says so ({@code hidden}) so a partial total is never mistaken for the firm's. Read only; money is always two decimals.
 */
@Service
@RequiredArgsConstructor
public class LpReportService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final LpReportData data;
    private final LpWallGuard walls;

    private static LocalDate today() { return LocalDate.now(ZONE); }

    private static boolean hidden(LpWallGuard.Barred b) {
        return !(b.matterIds().equals(List.of(LpWallGuard.Barred.NONE_ID)) && b.clientIds().equals(List.of(LpWallGuard.Barred.NONE_ID)));
    }

    // ---- the reports ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MattersReport matters(TenantId tenant) {
        var b = walls.barred(tenant);
        List<MatterCount> rows = data.matterCounts(tenant, b.matterIds(), b.clientIds());
        long total = rows.stream().mapToLong(MatterCount::count).sum();
        long open = rows.stream().filter(r -> "OPEN".equals(r.status()) || "ON_HOLD".equals(r.status())).mapToLong(MatterCount::count).sum();
        return new MattersReport(rows, total, open, hidden(b));
    }

    @Transactional(readOnly = true)
    public UnbilledReport unbilled(TenantId tenant) {
        var b = walls.barred(tenant);
        List<UnbilledRow> got = data.unbilled(tenant, b.matterIds(), b.clientIds(), ReportRules.ROW_LIMIT + 1);
        boolean truncated = got.size() > ReportRules.ROW_LIMIT;
        List<UnbilledRow> rows = new ArrayList<>();
        for (UnbilledRow r : truncated ? got.subList(0, ReportRules.ROW_LIMIT) : got)
            rows.add(new UnbilledRow(r.matterId(), r.matterLabel(), r.clientName(), ReportRules.money(r.hours()), ReportRules.money(r.timeValue()), ReportRules.money(r.disbursements()), ReportRules.money(r.timeValue()).add(ReportRules.money(r.disbursements()))));
        BigDecimal hours = BigDecimal.ZERO.setScale(2), value = hours, disb = hours;
        for (UnbilledRow r : rows) { hours = hours.add(ReportRules.money(r.hours())); value = value.add(r.timeValue()); disb = disb.add(r.disbursements()); }
        return new UnbilledReport(rows, hours, value, disb, value.add(disb), truncated, hidden(b));
    }

    @Transactional(readOnly = true)
    public DebtorsReport debtors(TenantId tenant) {
        var b = walls.barred(tenant);
        LocalDate today = today();
        List<DebtorRow> got = data.openInvoices(tenant, b.matterIds(), b.clientIds(), ReportRules.ROW_LIMIT + 1);
        boolean truncated = got.size() > ReportRules.ROW_LIMIT;
        List<DebtorRow> rows = new ArrayList<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (String[] k : ReportRules.BUCKETS) amounts.put(k[0], BigDecimal.ZERO.setScale(2));
        BigDecimal owing = BigDecimal.ZERO.setScale(2), overdue = owing;
        for (DebtorRow r : truncated ? got.subList(0, ReportRules.ROW_LIMIT) : got) {
            BigDecimal out = ReportRules.outstanding(r.total(), r.paid());
            if (out.signum() == 0) continue;
            long days = ReportRules.daysOverdue(r.dueDate(), today);
            String bucket = ReportRules.bucket(days);
            rows.add(new DebtorRow(r.invoiceId(), r.invoiceNumber(), r.clientName(), r.matterLabel(), r.issueDate(), r.dueDate(), ReportRules.money(r.total()), ReportRules.money(r.paid()), out, days, bucket));
            amounts.merge(bucket, out, BigDecimal::add); counts.merge(bucket, 1L, Long::sum);
            owing = owing.add(out);
            if (days > 0) overdue = overdue.add(out);
        }
        List<Bucket> buckets = new ArrayList<>();
        for (String[] k : ReportRules.BUCKETS) buckets.add(new Bucket(k[0], k[1], amounts.get(k[0]), counts.getOrDefault(k[0], 0L)));
        return new DebtorsReport(rows, buckets, owing, overdue, truncated, hidden(b));
    }

    @Transactional(readOnly = true)
    public BillingReport billing(TenantId tenant, LocalDate from, LocalDate to) {
        String problem = ReportRules.rangeProblem(from, to);
        if (problem != null) throw new IllegalArgumentException(problem);
        var b = walls.barred(tenant);
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal[]> sums = new TreeMap<>(); // invoiced, paid, written off
        final BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal invoiced = zero, vat = zero, paid = zero, off = zero;
        for (BillingRow r : data.invoicesIssued(tenant, from, to, b.matterIds(), b.clientIds())) {
            boolean writtenOff = "WRITTEN_OFF".equals(r.status());
            BigDecimal[] m = sums.computeIfAbsent(r.month(), k -> new BigDecimal[]{zero, zero, zero});
            counts.computeIfAbsent(r.month(), k -> new long[1])[0] += r.invoices();
            if (writtenOff) { m[2] = m[2].add(ReportRules.money(r.total())); off = off.add(ReportRules.money(r.total())); continue; }
            m[0] = m[0].add(ReportRules.money(r.total())); m[1] = m[1].add(ReportRules.money(r.paid()));
            invoiced = invoiced.add(ReportRules.money(r.total())); vat = vat.add(ReportRules.money(r.vat())); paid = paid.add(ReportRules.money(r.paid()));
        }
        List<BillingMonth> months = new ArrayList<>();
        for (var e : sums.entrySet()) months.add(new BillingMonth(e.getKey(), counts.get(e.getKey())[0], e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        return new BillingReport(from, to, months, invoiced, vat, paid, off, ReportRules.percent(paid, invoiced), hidden(b));
    }

    @Transactional(readOnly = true)
    public TrustReport trust(TenantId tenant, LocalDate asAt) {
        LocalDate day = asAt == null ? today() : asAt;
        if (day.isAfter(today())) throw new IllegalArgumentException("The date cannot be in the future");
        var b = walls.barred(tenant);
        List<TrustRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long overdrawn = 0;
        for (TrustRow r : data.trustBalances(tenant, day, b.matterIds(), b.clientIds())) {
            BigDecimal bal = ReportRules.money(r.balance());
            rows.add(new TrustRow(r.clientId(), r.clientName(), bal, r.lastMovement()));
            total = total.add(bal);
            if (bal.signum() < 0) overdrawn++;
        }
        return new TrustReport(day, rows, total, overdrawn, hidden(b));
    }

    // ---- CSV -----------------------------------------------------------------------------------------------------------

    public String mattersCsv(TenantId tenant) {
        return ReportRules.csv(List.of("Status", "Kind of matter", "Matters"), matters(tenant).rows().stream().map(r -> List.<Object>of(r.status(), r.matterType(), r.count())).toList());
    }

    public String unbilledCsv(TenantId tenant) {
        return ReportRules.csv(List.of("Matter", "Client", "Unbilled hours", "Time value", "Disbursements", "Total"),
                unbilled(tenant).rows().stream().map(r -> List.<Object>of(r.matterLabel(), r.clientName(), r.hours(), r.timeValue(), r.disbursements(), r.total())).toList());
    }

    public String debtorsCsv(TenantId tenant) {
        List<List<Object>> rows = new ArrayList<>();
        for (DebtorRow r : debtors(tenant).rows()) rows.add(Arrays.asList(r.invoiceNumber(), r.clientName(), r.matterLabel(), r.issueDate(), r.dueDate(), r.total(), r.paid(), r.outstanding(), r.daysOverdue(), r.bucket()));
        return ReportRules.csv(List.of("Invoice", "Client", "Matter", "Issued", "Due", "Total", "Paid", "Outstanding", "Days overdue", "Ageing"), rows);
    }

    public String billingCsv(TenantId tenant, LocalDate from, LocalDate to) {
        return ReportRules.csv(List.of("Month", "Invoices", "Invoiced (incl. VAT)", "Paid", "Written off"),
                billing(tenant, from, to).months().stream().map(m -> List.<Object>of(m.month(), m.invoices(), m.invoiced(), m.paid(), m.writtenOff())).toList());
    }

    public String trustCsv(TenantId tenant, LocalDate asAt) {
        List<List<Object>> rows = new ArrayList<>();
        for (TrustRow r : trust(tenant, asAt).rows()) rows.add(Arrays.asList(r.clientName(), r.balance(), r.lastMovement()));
        return ReportRules.csv(List.of("Client", "Trust balance", "Last movement"), rows);
    }
}
