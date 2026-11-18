package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.DivisionSalesDtos.Report;
import za.co.handyflow.platform.fuel.dto.DivisionSalesDtos.Row;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Revenue and sales by division, read-only. What counts in each division is written on {@link za.co.handyflow.platform.fuel.dto.DivisionSalesDtos}.
 * Tank-based figures (loads, shifts, dispatches) count only the tanks the caller may see; the truck stop has no tank, so it is shown when the
 * caller's scope includes the Truck stop division.
 */
@Service
@RequiredArgsConstructor
public class FuelDivisionSalesService {

    private final FuelDeliveryRepository deliveries;
    private final FuelDispatchRepository dispatches;
    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;
    private final FuelTruckStopStayRepository stays;
    private final FuelShiftReportClock clock;
    private final FuelDivisionGuard divisions;
    private final FuelScopeService scopes;

    /** The order the divisions are shown in. */
    static final List<FuelDivision> ORDER = List.of(FuelDivision.DISTRIBUTION, FuelDivision.RETAIL_FORECOURT, FuelDivision.WHOLESALE_SUPPLY, FuelDivision.TRUCK_STOP);

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, UUID userId, int days, boolean includeMoney, Set<UUID> allowedTankIds) {
        int span = Math.max(1, Math.min(days, 366));
        var basis = clock.basis(tenantId);
        ZoneId zone = basis.zone();
        LocalDate to = LocalDate.now(zone), from = to.minusDays(span - 1L);
        LocalDate prevTo = from.minusDays(1), prevFrom = prevTo.minusDays(span - 1L);
        Instant fromI = from.atStartOfDay(zone).toInstant(), toI = to.plusDays(1).atStartOfDay(zone).toInstant();
        Instant prevFromI = prevFrom.atStartOfDay(zone).toInstant(), prevToI = prevTo.plusDays(1).atStartOfDay(zone).toInstant();

        Set<FuelDivision> active = divisions.activeDivisions(tenantId);
        var scope = scopes.scopeOf(tenantId, userId);
        Map<FuelDivision, Acc> acc = new EnumMap<>(FuelDivision.class);
        for (FuelDivision d : ORDER) if (active.contains(d) && scope.allowsAnyOf(Set.of(d))) acc.put(d, new Acc(span));

        Window w = new Window(from, zone, fromI, toI, prevFromI, prevToI);

        Acc dist = acc.get(FuelDivision.DISTRIBUTION);
        if (dist != null) {
            for (FuelDelivery d : deliveries.findDeliveredBetween(tenantId, prevFromI, toI.minusMillis(1))) {
                if (d.getReversedAt() != null || d.getLitresDelivered() == null || d.getDeliveredAt() == null) continue;
                if (allowedTankIds != null && !allowedTankIds.contains(d.getTankId())) continue;
                BigDecimal litres = d.getLitresDelivered();
                BigDecimal revenue = d.getTotalAmount() != null ? d.getTotalAmount() : d.getPricePerLitre() == null ? BigDecimal.ZERO : litres.multiply(d.getPricePerLitre());
                dist.add(w, d.getDeliveredAt(), litres, revenue);
            }
        }

        Acc retail = acc.get(FuelDivision.RETAIL_FORECOURT);
        if (retail != null) {
            Map<UUID, FuelShift> counted = new HashMap<>();
            for (FuelShift s : shifts.findClosedSince(tenantId, prevFromI)) {
                Instant at = basis.closedFor(s);
                if (at != null && !at.isBefore(prevFromI) && at.isBefore(toI)) counted.put(s.getId(), s);
            }
            Set<UUID> seen = new HashSet<>();
            if (!counted.isEmpty()) {
                for (var r : readings.findByShifts(tenantId, counted.keySet())) {
                    if (r.getLitresSold() == null) continue;
                    if (allowedTankIds != null && !allowedTankIds.contains(r.getTankId())) continue;
                    FuelShift s = counted.get(r.getShiftId());
                    boolean first = seen.add(r.getShiftId());
                    retail.add(w, basis.closedFor(s), r.getLitresSold(), r.getSalesValue() == null ? BigDecimal.ZERO : r.getSalesValue(), first);
                }
            }
        }

        Acc wholesale = acc.get(FuelDivision.WHOLESALE_SUPPLY);
        if (wholesale != null) {
            for (FuelDispatch d : dispatches.findByTenantAndDispatchedAtBetween(tenantId, prevFromI, toI.minusMillis(1))) {
                if (d.getPricePerLitre() == null || d.getLitresDispensed() == null || d.isDeliveredByTransfer() || d.getReversedAt() != null) continue;
                if (allowedTankIds != null && !allowedTankIds.contains(d.getTankId())) continue;
                wholesale.add(w, d.getDispatchedAt(), d.getLitresDispensed(), d.getLitresDispensed().multiply(d.getPricePerLitre()));
            }
        }

        Acc truck = acc.get(FuelDivision.TRUCK_STOP);
        if (truck != null) {
            for (FuelTruckStopStay s : stays.findCheckedOutBetween(tenantId, prevFromI, toI)) {
                if (s.getCheckedOutAt() == null) continue;
                truck.add(w, s.getCheckedOutAt(), BigDecimal.ZERO, s.amount());
            }
        }

        List<Row> rows = new ArrayList<>();
        Acc total = new Acc(span);
        for (Map.Entry<FuelDivision, Acc> e : acc.entrySet()) {
            rows.add(e.getValue().row(e.getKey().name(), FuelDivisionRules.label(e.getKey()), includeMoney));
            total.addAll(e.getValue());
        }
        rows.sort(Comparator.comparing((Row r) -> ORDER.indexOf(FuelDivision.valueOf(r.division()))));
        return new Report(from, to, prevFrom, prevTo, includeMoney, rows, total.row("TOTAL", "All divisions", includeMoney));
    }

    private record Window(LocalDate from, ZoneId zone, Instant fromI, Instant toI, Instant prevFromI, Instant prevToI) {}

    private static BigDecimal m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private static final class Acc {
        long count, previousCount;
        BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, previousLitres = BigDecimal.ZERO, previousRevenue = BigDecimal.ZERO;
        final BigDecimal[] daily;

        Acc(int span) { daily = new BigDecimal[span]; Arrays.fill(daily, BigDecimal.ZERO); }

        /** One sale. {@code counts} false adds litres and value without adding to the count (a shift has one reading per pump). */
        void add(Window w, Instant at, BigDecimal l, BigDecimal rev, boolean counts) {
            if (at == null) return;
            if (!at.isBefore(w.toI())) return;
            if (!at.isBefore(w.fromI())) {
                if (counts) count++;
                litres = litres.add(l); revenue = revenue.add(rev);
                int i = (int) java.time.temporal.ChronoUnit.DAYS.between(w.from(), at.atZone(w.zone()).toLocalDate());
                if (i >= 0 && i < daily.length) daily[i] = daily[i].add(rev);
            } else if (!at.isBefore(w.prevFromI())) {
                if (counts) previousCount++;
                previousLitres = previousLitres.add(l); previousRevenue = previousRevenue.add(rev);
            }
        }

        void add(Window w, Instant at, BigDecimal l, BigDecimal rev) { add(w, at, l, rev, true); }

        void addAll(Acc o) {
            count += o.count; previousCount += o.previousCount;
            litres = litres.add(o.litres); revenue = revenue.add(o.revenue);
            previousLitres = previousLitres.add(o.previousLitres); previousRevenue = previousRevenue.add(o.previousRevenue);
            for (int i = 0; i < daily.length; i++) daily[i] = daily[i].add(o.daily[i]);
        }

        Row row(String division, String label, boolean money) {
            return new Row(division, label, count, m(litres), money ? m(revenue) : null, money ? Arrays.stream(daily).map(FuelDivisionSalesService::m).toList() : null,
                    previousCount, m(previousLitres), money ? m(previousRevenue) : null);
        }
    }
}
