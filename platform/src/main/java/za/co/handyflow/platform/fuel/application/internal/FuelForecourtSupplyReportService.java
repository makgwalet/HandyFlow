package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftReading;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Forecourt;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Report;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Supplier;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 8d: forecourt sales grouped by supplying depot. A forecourt is the depot that holds the pump tanks; each depot may record the depot that
 * supplies it. A reading belongs to the depot of its tank; a tank with no depot, or a depot with no supplier, goes in "No supplier set" so
 * nothing drops out of the totals. A forecourt with a saved supply split (percentages across several supplying depots) has its litres and sales shared out by those percentages. Only closed, not-reversed shifts count. Read-only; it changes nothing.
 */
@Service
@RequiredArgsConstructor
public class FuelForecourtSupplyReportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final za.co.handyflow.platform.fuel.domain.repository.FuelDepotSupplyShareRepository supplyShares;
    private final FuelShiftReportClock clock;

    public Report report(TenantId tenantId, int days, boolean includeMoney) { return report(tenantId, days, includeMoney, null); }

    /**
     * {@code allowedDepotIds} null means no depot limit. Otherwise only forecourts (depots) in that set are counted, so totals cover what the user may see;
     * a supplier is still named above the forecourts it supplies, because the forecourt's own report needs it. A tank in no depot is left out for a limited user.
     */
    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, int days, boolean includeMoney, Set<UUID> allowedDepotIds) {
        int span = Math.max(1, Math.min(days, 366));
        var basis = clock.basis(tenantId);
        LocalDate to = LocalDate.now(basis.zone()), from = to.minusDays(span - 1L);
        var fromInstant = from.atStartOfDay(basis.zone()).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(basis.zone()).toInstant();

        List<FuelShift> closed = new ArrayList<>();
        // The system's closing time is always on or after the provider's (an import is accepted afterwards), so this query never misses a shift whose provider time is in range.
        for (FuelShift s : shifts.findClosedSince(tenantId, fromInstant)) {
            Instant at = basis.closedFor(s);
            if (at != null && !at.isBefore(fromInstant) && at.isBefore(toInstant)) closed.add(s);
        }
        Map<UUID, FuelDepot> depotById = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) depotById.put(d.getId(), d);
        Map<UUID, UUID> tankDepot = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankDepot.put(t.getId(), t.getDepotId());
        // A forecourt with a saved split has its sales shared out by percentage; every other forecourt uses its single supplier.
        Map<UUID, List<za.co.handyflow.platform.fuel.domain.model.FuelDepotSupplyShare>> splits = new HashMap<>();
        for (var sh : supplyShares.findAllForTenant(tenantId)) if (depotById.containsKey(sh.getSupplyingDepotId())) splits.computeIfAbsent(sh.getDepotId(), k -> new ArrayList<>()).add(sh);

        // supplier id (null = none) -> forecourt depot id (null = tank in no depot) -> totals
        Map<UUID, Map<UUID, Acc>> grouped = new LinkedHashMap<>();
        long left = 0;
        if (!closed.isEmpty()) {
            Map<UUID, Set<UUID>> shiftsPerForecourt = new HashMap<>();
            Set<UUID> ids = new HashSet<>();
            for (FuelShift s : closed) ids.add(s.getId());
            for (FuelShiftReading r : readings.findByShifts(tenantId, ids)) {
                if (r.getLitresSold() == null) { left++; continue; }
                UUID forecourt = tankDepot.get(r.getTankId());
                if (allowedDepotIds != null && (forecourt == null || !allowedDepotIds.contains(forecourt))) continue;
                FuelDepot fd = forecourt == null ? null : depotById.get(forecourt);
                var split = fd == null ? null : splits.get(fd.getId());
                if (split != null && !split.isEmpty()) {
                    // Share litres and sales by percentage; the last line takes the rounding remainder so the pieces add back to the reading.
                    BigDecimal litLeft = r.getLitresSold(), salesLeft = r.getSalesValue();
                    for (int i = 0; i < split.size(); i++) {
                        var sh = split.get(i);
                        boolean last = i == split.size() - 1;
                        BigDecimal lit = last ? litLeft : r.getLitresSold().multiply(sh.getPercentage()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                        BigDecimal sal = salesLeft == null ? null : (last ? salesLeft : r.getSalesValue().multiply(sh.getPercentage()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
                        litLeft = litLeft.subtract(lit); if (salesLeft != null) salesLeft = salesLeft.subtract(sal);
                        Acc a = grouped.computeIfAbsent(sh.getSupplyingDepotId(), k -> new LinkedHashMap<>()).computeIfAbsent(fd.getId(), k -> new Acc());
                        a.litres = a.litres.add(lit); if (sal != null) a.sales = a.sales.add(sal);
                        a.shiftIds.add(r.getShiftId());
                    }
                    continue;
                }
                UUID supplier = fd == null ? null : fd.getSupplyingDepotId();
                if (supplier != null && !depotById.containsKey(supplier)) supplier = null; // supplier since deactivated
                Acc a = grouped.computeIfAbsent(supplier, k -> new LinkedHashMap<>()).computeIfAbsent(fd == null ? null : fd.getId(), k -> new Acc());
                a.litres = a.litres.add(r.getLitresSold());
                if (r.getSalesValue() != null) a.sales = a.sales.add(r.getSalesValue());
                a.shiftIds.add(r.getShiftId());
            }
        }

        List<Supplier> out = new ArrayList<>();
        BigDecimal totalLitres = BigDecimal.ZERO, totalSales = BigDecimal.ZERO;
        for (Map.Entry<UUID, Map<UUID, Acc>> e : grouped.entrySet()) {
            List<Forecourt> rows = new ArrayList<>();
            BigDecimal litres = BigDecimal.ZERO, sales = BigDecimal.ZERO;
            for (Map.Entry<UUID, Acc> f : e.getValue().entrySet()) {
                Acc a = f.getValue();
                String name = f.getKey() == null ? "Tank not in a depot" : depotById.get(f.getKey()).getName();
                rows.add(new Forecourt(f.getKey(), name, a.shiftIds.size(), a.litres, includeMoney ? a.sales : null));
                litres = litres.add(a.litres); sales = sales.add(a.sales);
            }
            rows.sort(Comparator.comparing(Forecourt::litresSold).reversed().thenComparing(Forecourt::name));
            boolean none = e.getKey() == null;
            out.add(new Supplier(e.getKey(), none ? "No supplier set" : depotById.get(e.getKey()).getName(), none, rows, litres, includeMoney ? sales : null));
            totalLitres = totalLitres.add(litres); totalSales = totalSales.add(sales);
        }
        // Real suppliers first by litres, the "no supplier" bucket last.
        out.sort(Comparator.comparing(Supplier::unassigned).thenComparing(Supplier::litresSold, Comparator.reverseOrder()).thenComparing(Supplier::name));
        return new Report(from, to, includeMoney, out, totalLitres, includeMoney ? totalSales : null, left, clock.describe(tenantId, basis, closed));
    }

    private static final class Acc {
        BigDecimal litres = BigDecimal.ZERO, sales = BigDecimal.ZERO;
        Set<UUID> shiftIds = new HashSet<>();
    }
}
