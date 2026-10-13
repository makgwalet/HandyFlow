package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.ExposureSnapshot;
import za.co.handyflow.platform.debtcollection.domain.model.LedgerEntryType;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;
import za.co.handyflow.platform.debtcollection.domain.repository.ExposureSnapshotRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.RecoveryEntryRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Recovery reporting from the ledger (real recovered cash, by month, source and collector) and the exposure trend
 * (daily snapshots of outstanding + the day's net recovery). This supplies the "Recovered" figures and the trend line
 * the dashboard could not honestly show before the ledger existed. Both start at deployment: nothing is back-filled.
 * Net recovered = payments − reversals, by effective date. Fees are reported separately and are not "recovered debt".
 */
@Service
@RequiredArgsConstructor
public class RecoveryReportService {

    private static final int MAX_RANGE_DAYS = 366;

    public record MonthPoint(String month, BigDecimal netRecovered) {}

    public record CollectorRecovery(UUID userId, String userName, BigDecimal netRecovered, long payments) {}

    public record RecoveryReport(LocalDate from, LocalDate to, BigDecimal paymentsGross, BigDecimal reversals,
                                 BigDecimal netRecovered, BigDecimal feesCharged, BigDecimal feesWaived,
                                 List<MonthPoint> byMonth, Map<PaymentSource, BigDecimal> bySource,
                                 List<CollectorRecovery> byCollector) {}

    public record TrendPoint(LocalDate date, BigDecimal outstanding, int activeCases, BigDecimal recoveredNet) {}

    private final RecoveryEntryRepository entryRepository;
    private final ExposureSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public RecoveryReport recovery(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        validate(start, end);
        BigDecimal gross = BigDecimal.ZERO, rev = BigDecimal.ZERO, charged = BigDecimal.ZERO, waived = BigDecimal.ZERO;
        Map<YearMonth, BigDecimal> months = new TreeMap<>();
        Map<PaymentSource, BigDecimal> sources = new EnumMap<>(PaymentSource.class);
        Map<UUID, BigDecimal> collectorNet = new HashMap<>();
        Map<UUID, Long> collectorCount = new HashMap<>();
        Map<UUID, String> collectorName = new HashMap<>();
        for (RecoveryEntry e : entryRepository.findBetween(tenantId, start, end)) {
            BigDecimal signed = switch (e.getEntryType()) {
                case PAYMENT -> e.getAmount();
                case REVERSAL -> e.getAmount().negate();
                default -> BigDecimal.ZERO;
            };
            switch (e.getEntryType()) {
                case PAYMENT -> gross = gross.add(e.getAmount());
                case REVERSAL -> rev = rev.add(e.getAmount());
                case FEE_CHARGE -> charged = charged.add(e.getAmount());
                case FEE_WAIVER -> waived = waived.add(e.getAmount());
            }
            if (e.getEntryType() == LedgerEntryType.PAYMENT || e.getEntryType() == LedgerEntryType.REVERSAL) {
                months.merge(YearMonth.from(e.getEffectiveDate()), signed, BigDecimal::add);
                if (e.getSource() != null) sources.merge(e.getSource(), signed, BigDecimal::add);
                UUID u = e.getRecordedByUserId();
                collectorNet.merge(u, signed, BigDecimal::add);
                if (e.getEntryType() == LedgerEntryType.PAYMENT) collectorCount.merge(u, 1L, Long::sum);
                if (e.getRecordedByUserName() != null) collectorName.putIfAbsent(u, e.getRecordedByUserName());
            }
        }
        List<MonthPoint> byMonth = new ArrayList<>();
        months.forEach((m, v) -> byMonth.add(new MonthPoint(m.toString(), v)));
        List<CollectorRecovery> byCollector = collectorNet.entrySet().stream()
                .map(en -> new CollectorRecovery(en.getKey(),
                        en.getKey() == null ? "System / unattributed" : collectorName.getOrDefault(en.getKey(), en.getKey().toString()),
                        en.getValue(), collectorCount.getOrDefault(en.getKey(), 0L)))
                .sorted(Comparator.comparing(CollectorRecovery::netRecovered).reversed()).toList();
        return new RecoveryReport(start, end, gross, rev, gross.subtract(rev), charged, waived, byMonth, sources, byCollector);
    }

    /** One point per snapshot day: outstanding then, and net recovered on that day. Days without a snapshot have no point. */
    @Transactional(readOnly = true)
    public List<TrendPoint> trend(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(89);
        validate(start, end);
        Map<LocalDate, BigDecimal> recoveredByDay = new LinkedHashMap<>();
        for (RecoveryEntry e : entryRepository.findBetween(tenantId, start, end)) {
            if (e.getEntryType() == LedgerEntryType.PAYMENT) recoveredByDay.merge(e.getEffectiveDate(), e.getAmount(), BigDecimal::add);
            if (e.getEntryType() == LedgerEntryType.REVERSAL) recoveredByDay.merge(e.getEffectiveDate(), e.getAmount().negate(), BigDecimal::add);
        }
        List<TrendPoint> out = new ArrayList<>();
        for (ExposureSnapshot s : snapshotRepository.findBetween(tenantId, start, end)) {
            out.add(new TrendPoint(s.getSnapshotDate(), s.getTotalOutstanding(), s.getActiveCases(),
                    recoveredByDay.getOrDefault(s.getSnapshotDate(), BigDecimal.ZERO)));
        }
        return out;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
