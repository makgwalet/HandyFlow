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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 8d: forecourt sales grouped by supplying depot. A forecourt is the depot that holds the pump tanks; each depot may record the depot that
 * supplies it. A reading belongs to the depot of its tank; a tank with no depot, or a depot with no supplier, goes in "No supplier set" so
 * nothing drops out of the totals. Only closed, not-reversed shifts count. Read-only; it changes nothing.
 */
@Service
@RequiredArgsConstructor
public class FuelForecourtSupplyReportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, int days, boolean includeMoney) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        var fromInstant = from.atStartOfDay(SAST).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(SAST).toInstant();

        List<FuelShift> closed = new ArrayList<>();
        for (FuelShift s : shifts.findClosedSince(tenantId, fromInstant)) if (s.getClosedAt() != null && s.getClosedAt().isBefore(toInstant)) closed.add(s);
        Map<UUID, FuelDepot> depotById = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) depotById.put(d.getId(), d);
        Map<UUID, UUID> tankDepot = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankDepot.put(t.getId(), t.getDepotId());

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
                FuelDepot fd = forecourt == null ? null : depotById.get(forecourt);
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
        return new Report(from, to, includeMoney, out, totalLitres, includeMoney ? totalSales : null, left);
    }

    private static final class Acc {
        BigDecimal litres = BigDecimal.ZERO, sales = BigDecimal.ZERO;
        Set<UUID> shiftIds = new HashSet<>();
    }
}
