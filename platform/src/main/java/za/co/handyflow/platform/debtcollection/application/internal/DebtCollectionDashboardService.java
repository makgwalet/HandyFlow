package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.application.internal.PtpService.PtpMetrics;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PtpStatus;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionReportRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collections dashboard: KPIs, aging, top debtors and collector performance. Read-only; no new tables.
 * <p>
 * WHAT "OUTSTANDING" MEANS HERE. Every figure is the LIVE outstanding of invoices linked to non-terminal
 * cases (terminal = CLOSED, WRITTEN_OFF, SETTLED), read from InvoicingFacade at request time — not the
 * snapshot stored on the case, which can be stale. An invoice no longer in invoicing's outstanding list
 * counts as paid. This is collection-case exposure, not the tenant's whole receivables book (accounting's
 * AR aging covers that). HANDED_TO_LEGAL cases stay in exposure until they reach a terminal status.
 * <p>
 * WHAT IS DELIBERATELY NOT HERE (needs data that doesn't exist yet):
 * <ul>
 *   <li>"Recovered" cash and a recovered-vs-outstanding trend line — there is no recovery ledger or daily
 *   balance snapshot. The closest honest figure is PTP-confirmed recoveries (PtpMetrics.receivedValue),
 *   which is what this reports; do not present it as total cash recovered.</li>
 *   <li>Outstanding "vs last month" — a stock measure with no history. Flow measures (cases closed,
 *   success rate, PTP-confirmed value) do get a previous-period comparison.</li>
 * </ul>
 * COLLECTION SUCCESS RATE = cases closed in the period with reason PAID_IN_FULL or SETTLED_PARTIAL ÷ all
 * cases closed in the period (by count; 0 when none closed). Write-offs do not set a closed date, so they
 * are reported separately (writtenOffToDate) rather than silently dragging the rate.
 * <p>
 * Loads the tenant's cases and its outstanding-invoice list once per request and aggregates in Java — fine
 * at current per-tenant volumes (same trade-off DebtCollectionCaseService already documents); revisit with
 * SQL aggregation or a snapshot table if a tenant holds tens of thousands of cases.
 */
@Service
@RequiredArgsConstructor
public class DebtCollectionDashboardService {

    private static final Set<CaseStatus> TERMINAL = EnumSet.of(CaseStatus.CLOSED, CaseStatus.WRITTEN_OFF, CaseStatus.SETTLED);
    private static final Set<ClosureReason> SUCCESS = EnumSet.of(ClosureReason.PAID_IN_FULL, ClosureReason.SETTLED_PARTIAL);
    private static final int MAX_RANGE_DAYS = 366;

    public record PeriodStats(LocalDate from, LocalDate to, long casesOpened, long casesClosed,
                              long casesClosedSuccessfully, BigDecimal successRate, PtpMetrics ptp) {}

    public record Summary(LocalDate asOf, BigDecimal totalOutstanding, long activeCases,
                          Map<CaseStatus, Long> casesByStatus, PeriodStats current, PeriodStats previous,
                          long openWorkItems, long overdueWorkItems, BigDecimal writtenOffToDate) {}

    public record AgingBucket(String label, BigDecimal amount, long invoiceCount, long caseCount, BigDecimal share) {}

    public record AgingReport(LocalDate asOf, BigDecimal totalOutstanding, List<AgingBucket> buckets) {}

    public record TopDebtor(UUID caseId, String caseNumber, String debtorName, CaseStatus status,
                            BigDecimal outstanding, BigDecimal snapshotOutstanding, long maxDaysOverdue,
                            String assignedToUserName, LocalDate lastContactDate, LocalDate nextActionDate) {}

    public record CollectorRow(UUID userId, String userName, long openCases, BigDecimal outstandingValue,
                               long openWorkItems, long overdueWorkItems, long contactsInPeriod,
                               long ptpKept, long ptpPartial, long ptpBroken, BigDecimal ptpPromisedValue,
                               BigDecimal ptpReceivedValue, BigDecimal ptpKeptRate) {}

    private record InvoiceLine(BigDecimal outstanding, long daysOverdue) {}

    private record Exposure(DebtCollectionCase debtCase, BigDecimal outstanding, long maxDaysOverdue,
                            List<InvoiceLine> lines) {}

    private record Data(List<DebtCollectionCase> all, List<Exposure> live) {}

    private final DebtCollectionCaseRepository caseRepository;
    private final DebtCollectionReportRepository reportRepository;
    private final InvoicingFacade invoicingFacade;
    private final PtpService ptpService;

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        validate(start, end);
        long len = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(len - 1);

        Data d = load(tenantId, today);
        BigDecimal total = d.live().stream().map(Exposure::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<CaseStatus, Long> byStatus = new EnumMap<>(CaseStatus.class);
        for (CaseStatus s : CaseStatus.values()) byStatus.put(s, 0L);
        d.all().forEach(c -> byStatus.merge(c.getStatus(), 1L, Long::sum));
        BigDecimal writtenOff = d.all().stream().filter(c -> c.getStatus() == CaseStatus.WRITTEN_OFF)
                .map(c -> c.getWriteOffAmount() != null ? c.getWriteOffAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long open = 0, overdue = 0;
        for (Object[] r : reportRepository.workItemCounts(tenantId, today)) {
            open += ((Number) r[2]).longValue();
            overdue += ((Number) r[3]).longValue();
        }
        return new Summary(today, total, d.live().size(), byStatus,
                period(tenantId, d.all(), start, end), period(tenantId, d.all(), prevStart, prevEnd),
                open, overdue, writtenOff);
    }

    @Transactional(readOnly = true)
    public AgingReport aging(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        Data d = load(tenantId, today);
        int n = AgingBuckets.LABELS.size();
        BigDecimal[] amounts = new BigDecimal[n];
        long[] invoices = new long[n];
        List<Set<UUID>> caseSets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            amounts[i] = BigDecimal.ZERO;
            caseSets.add(new java.util.HashSet<>());
        }
        for (Exposure e : d.live()) {
            for (InvoiceLine l : e.lines()) {
                int i = AgingBuckets.indexFor(l.daysOverdue());
                amounts[i] = amounts[i].add(l.outstanding());
                invoices[i]++;
                caseSets.get(i).add(e.debtCase().getId());
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal a : amounts) total = total.add(a);
        List<AgingBucket> buckets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            buckets.add(new AgingBucket(AgingBuckets.LABELS.get(i), amounts[i], invoices[i], caseSets.get(i).size(),
                    total.signum() == 0 ? BigDecimal.ZERO : amounts[i].divide(total, 4, RoundingMode.HALF_UP)));
        }
        return new AgingReport(today, total, buckets);
    }

    @Transactional(readOnly = true)
    public List<TopDebtor> topDebtors(TenantId tenantId, int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        return load(tenantId, LocalDate.now()).live().stream()
                .sorted(Comparator.comparing(Exposure::outstanding).reversed())
                .limit(cap)
                .map(e -> {
                    DebtCollectionCase c = e.debtCase();
                    return new TopDebtor(c.getId(), c.getCaseNumber(), c.getDebtorName(), c.getStatus(),
                            e.outstanding(), c.getTotalOutstanding(), e.maxDaysOverdue(), c.getAssignedToUserName(),
                            c.getLastContactDate(), c.getNextActionDate());
                }).toList();
    }

    /** Supervisor view. PTPs are attributed to whoever recorded them, which is an approximation of "the collector's promises". */
    @Transactional(readOnly = true)
    public List<CollectorRow> collectors(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        validate(start, end);

        Map<UUID, Acc> acc = new LinkedHashMap<>(); // null key = unassigned
        for (Exposure e : load(tenantId, today).live()) {
            Acc a = acc.computeIfAbsent(e.debtCase().getAssignedToUserId(), k -> new Acc());
            a.name(e.debtCase().getAssignedToUserName());
            a.openCases++;
            a.outstanding = a.outstanding.add(e.outstanding());
        }
        for (Object[] r : reportRepository.workItemCounts(tenantId, today)) {
            Acc a = acc.computeIfAbsent((UUID) r[0], k -> new Acc());
            a.name((String) r[1]);
            a.openItems = ((Number) r[2]).longValue();
            a.overdueItems = ((Number) r[3]).longValue();
        }
        for (Object[] r : reportRepository.contactCounts(tenantId, start, end)) {
            Acc a = acc.computeIfAbsent((UUID) r[0], k -> new Acc());
            a.name((String) r[1]);
            a.contacts = ((Number) r[2]).longValue();
        }
        for (Object[] r : reportRepository.ptpOutcomesByCreator(tenantId, start, end)) {
            Acc a = acc.computeIfAbsent((UUID) r[0], k -> new Acc());
            a.name((String) r[1]);
            long count = ((Number) r[3]).longValue();
            switch ((PtpStatus) r[2]) {
                case KEPT -> a.kept += count;
                case PARTIALLY_KEPT -> a.partial += count;
                case BROKEN -> a.broken += count;
                default -> { }
            }
            a.promised = a.promised.add((BigDecimal) r[4]);
            a.received = a.received.add((BigDecimal) r[5]);
        }
        return acc.entrySet().stream()
                .map(en -> en.getValue().toRow(en.getKey()))
                .sorted(Comparator.comparing(CollectorRow::outstandingValue).reversed())
                .toList();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private PeriodStats period(TenantId tenantId, List<DebtCollectionCase> all, LocalDate from, LocalDate to) {
        long opened = all.stream().filter(c -> inRange(c.getOpenedDate(), from, to)).count();
        List<DebtCollectionCase> closed = all.stream().filter(c -> inRange(c.getClosedDate(), from, to)).toList();
        long success = closed.stream().filter(c -> c.getClosureReason() != null && SUCCESS.contains(c.getClosureReason())).count();
        BigDecimal rate = closed.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(success).divide(BigDecimal.valueOf(closed.size()), 4, RoundingMode.HALF_UP);
        return new PeriodStats(from, to, opened, closed.size(), success, rate, ptpService.metrics(tenantId, from, to));
    }

    private Data load(TenantId tenantId, LocalDate today) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity(), (a, b) -> a));
        List<DebtCollectionCase> all = caseRepository.findAllActive(tenantId);
        List<Exposure> live = new ArrayList<>();
        for (DebtCollectionCase c : all) {
            if (TERMINAL.contains(c.getStatus())) continue;
            List<InvoiceLine> lines = new ArrayList<>();
            BigDecimal outstanding = BigDecimal.ZERO;
            long maxDays = 0;
            for (UUID invoiceId : c.getLinkedInvoiceIds()) {
                OutstandingInvoiceSummary inv = byId.get(invoiceId);
                if (inv == null) continue; // no longer outstanding => paid
                BigDecimal paid = inv.settled();   // finance patch F8: paid plus credit notes
                BigDecimal open = inv.total().subtract(paid);
                if (open.signum() <= 0) continue;
                long days = inv.dueDate() != null ? ChronoUnit.DAYS.between(inv.dueDate(), today) : 0;
                lines.add(new InvoiceLine(open, days));
                outstanding = outstanding.add(open);
                maxDays = Math.max(maxDays, days);
            }
            live.add(new Exposure(c, outstanding, maxDays, lines));
        }
        return new Data(all, live);
    }

    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    /** Mutable per-collector accumulator. */
    private static final class Acc {
        String name;
        long openCases, openItems, overdueItems, contacts, kept, partial, broken;
        BigDecimal outstanding = BigDecimal.ZERO, promised = BigDecimal.ZERO, received = BigDecimal.ZERO;

        void name(String n) { if (name == null && n != null && !n.isBlank()) name = n; }

        CollectorRow toRow(UUID userId) {
            long resolved = kept + partial + broken;
            return new CollectorRow(userId, userId == null ? "Unassigned" : (name != null ? name : userId.toString()),
                    openCases, outstanding, openItems, overdueItems, contacts, kept, partial, broken, promised,
                    received, resolved == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(kept).divide(BigDecimal.valueOf(resolved), 4, RoundingMode.HALF_UP));
        }
    }
}
