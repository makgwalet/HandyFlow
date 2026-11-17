package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.DipReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDispatchRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.dto.FuelDashboardSummary;
import za.co.handyflow.platform.fuel.dto.FuelDashboardSummary.NextDelivery;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The Fuel dashboard's headline figures, computed over the whole month and every open delivery, so they no longer depend on the first 200 rows of a list.
 * Everything is limited to the tanks the caller may see (the same scope as the lists): {@code null} means unrestricted, an empty set means nothing.
 * Read only.
 */
@Service
@RequiredArgsConstructor
public class FuelDashboardService {

    static final ZoneId SA = ZoneId.of("Africa/Johannesburg");
    static final BigDecimal VARIANCE_ALERT = new BigDecimal("-5");
    static final int NEXT_DELIVERIES = 8;
    static final long OVERDUE_AFTER_HOURS = 24;

    private final FuelDispatchRepository dispatches;
    private final FuelReceiptRepository receipts;
    private final DipReadingRepository dips;
    private final FuelDeliveryRepository deliveries;

    @Transactional(readOnly = true)
    public FuelDashboardSummary summary(TenantId tenantId, Set<UUID> allowedTankIds, Instant now) {
        LocalDate today = now.atZone(SA).toLocalDate();
        if (allowedTankIds != null && allowedTankIds.isEmpty()) return empty(today);
        Instant from = today.withDayOfMonth(1).atStartOfDay(SA).toInstant();
        Instant to = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(SA).toInstant();
        return build(today, now, allowedTankIds,
                dispatches.dashboardRows(tenantId, from, to),
                receipts.tankIdsReceivedBetween(tenantId, from, to),
                dips.tankIdsWithVarianceBelow(tenantId, from, to, VARIANCE_ALERT),
                deliveries.findOpen(tenantId));
    }

    /** Pure, so it can be tested without a database. Dispatch rows are [tankId, dispatchedAt, litresDispensed]. */
    static FuelDashboardSummary build(LocalDate today, Instant now, Set<UUID> allowed, List<Object[]> dispatchRows, List<UUID> receiptTanks,
                                      List<UUID> varianceTanks, List<FuelDelivery> open) {
        BigDecimal litres = BigDecimal.ZERO;
        int count = 0;
        List<BigDecimal> daily = new ArrayList<>();
        for (int i = 0; i < today.getDayOfMonth(); i++) daily.add(BigDecimal.ZERO);
        for (Object[] r : dispatchRows) {
            if (!visible(allowed, (UUID) r[0])) continue;
            BigDecimal l = r[2] == null ? BigDecimal.ZERO : (BigDecimal) r[2];
            litres = litres.add(l); count++;
            int day = ((Instant) r[1]).atZone(SA).getDayOfMonth();
            if (day >= 1 && day <= daily.size()) daily.set(day - 1, daily.get(day - 1).add(l));
        }
        int rec = (int) receiptTanks.stream().filter(t -> visible(allowed, t)).count();
        int neg = (int) varianceTanks.stream().filter(t -> visible(allowed, t)).count();

        List<FuelDelivery> mine = open.stream().filter(d -> visible(allowed, d.getTankId())).toList();
        int inTransit = (int) mine.stream().filter(d -> "IN_TRANSIT".equals(d.getStatus())).count();
        Instant cutoff = now.minusSeconds(OVERDUE_AFTER_HOURS * 3600);
        int overdue = (int) mine.stream().filter(d -> "SCHEDULED".equals(d.getStatus()) && d.getScheduledAt() != null && d.getScheduledAt().isBefore(cutoff)).count();
        List<NextDelivery> next = mine.stream()
                .sorted(Comparator.comparing(FuelDelivery::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(NEXT_DELIVERIES)
                .map(d -> new NextDelivery(d.getId(), d.getStatus(), d.getFuelType(), d.getLitresOrdered(), d.getScheduledAt())).toList();
        return new FuelDashboardSummary(today, litres, count, rec, daily, neg, mine.size(), inTransit, overdue, next);
    }

    private static boolean visible(Set<UUID> allowed, UUID tankId) { return allowed == null || (tankId != null && allowed.contains(tankId)); }

    private static FuelDashboardSummary empty(LocalDate today) {
        List<BigDecimal> daily = new ArrayList<>();
        for (int i = 0; i < today.getDayOfMonth(); i++) daily.add(BigDecimal.ZERO);
        return new FuelDashboardSummary(today, BigDecimal.ZERO, 0, 0, daily, 0, 0, 0, 0, List.of());
    }
}
