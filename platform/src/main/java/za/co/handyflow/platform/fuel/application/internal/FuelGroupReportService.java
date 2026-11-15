package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryVariance;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.GroupReportDtos.Report;
import za.co.handyflow.platform.fuel.dto.GroupReportDtos.Row;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 6a: the group at a glance. A delivery, a tank and an open variance belong to the legal entity that owns the depot the tank
 * is in; anything with no depot or a depot with no entity goes in "Not assigned to a legal entity" so nothing drops out of the totals.
 * Covers distribution deliveries, stock, delivery variances and forecourt sales (each shift reading belongs to the entity of its tank's depot).
 * Read-only; it changes nothing.
 */
@Service
@RequiredArgsConstructor
public class FuelGroupReportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelDeliveryRepository deliveries;
    private final FuelDeliveryVarianceRepository variances;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final FuelLegalEntityRepository legalEntities;
    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, int days, boolean includeMoney) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        LocalDate prevTo = from.minusDays(1), prevFrom = prevTo.minusDays(span - 1L);

        Map<UUID, UUID> depotEntity = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) depotEntity.put(d.getId(), d.getLegalEntityId());
        List<FuelTank> activeTanks = tanks.findAllActive(tenantId);
        Map<UUID, FuelTank> tankById = new HashMap<>();
        for (FuelTank t : activeTanks) tankById.put(t.getId(), t);

        List<FuelDelivery> current = deliveries.findDeliveredBetween(tenantId, from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
        List<FuelDelivery> previous = deliveries.findDeliveredBetween(tenantId, prevFrom.atStartOfDay(SAST).toInstant(), prevTo.plusDays(1).atStartOfDay(SAST).toInstant());

        // Tanks removed since a delivery was made still own that history: look them up too, but only this tenant's.
        Set<UUID> missing = new HashSet<>();
        for (FuelDelivery d : concat(current, previous)) if (d.getTankId() != null && !tankById.containsKey(d.getTankId())) missing.add(d.getTankId());
        Map<UUID, FuelTank> historicTanks = new HashMap<>();
        if (!missing.isEmpty()) for (FuelTank t : tanks.findAllById(missing)) if (tenantId.equals(t.getTenantId())) historicTanks.put(t.getId(), t);

        Map<UUID, Acc> accs = new LinkedHashMap<>();
        Acc unassigned = new Acc();
        for (FuelLegalEntity e : legalEntities.findAllActive(tenantId)) accs.put(e.getId(), new Acc());
        Map<UUID, String> names = new HashMap<>();
        for (FuelLegalEntity e : legalEntities.findAllActive(tenantId)) names.put(e.getId(), e.getName());

        long left = 0;
        for (FuelDelivery d : current) { if (!add(owner(d, tankById, historicTanks, depotEntity, accs, unassigned), d, false)) left++; }
        for (FuelDelivery d : previous) add(owner(d, tankById, historicTanks, depotEntity, accs, unassigned), d, true);

        for (FuelTank t : activeTanks) {
            Acc a = ownerOfTank(t, depotEntity, accs, unassigned);
            BigDecimal litres = t.getCurrentLitres() == null ? BigDecimal.ZERO : t.getCurrentLitres();
            a.stockLitres = a.stockLitres.add(litres);
            if (litres.signum() > 0) {
                if (t.getCostPerLitreWac() == null) a.tanksWithoutCost++; else a.stockValue = a.stockValue.add(litres.multiply(t.getCostPerLitreWac()));
            }
        }

        // Forecourt sales in the period, by the entity of each pump's tank. A tank that has since been removed falls in the not-assigned bucket.
        var fromInstant = from.atStartOfDay(SAST).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(SAST).toInstant();
        Set<UUID> shiftIds = new HashSet<>();
        for (var s : shifts.findClosedSince(tenantId, fromInstant)) if (s.getClosedAt() != null && s.getClosedAt().isBefore(toInstant)) shiftIds.add(s.getId());
        if (!shiftIds.isEmpty()) {
            for (var r : readings.findByShifts(tenantId, shiftIds)) {
                if (r.getLitresSold() == null) continue;
                FuelTank t = tankById.get(r.getTankId());
                Acc a = t == null ? unassigned : ownerOfTank(t, depotEntity, accs, unassigned);
                a.forecourtLitres = a.forecourtLitres.add(r.getLitresSold());
                if (r.getSalesValue() != null) a.forecourtSales = a.forecourtSales.add(r.getSalesValue());
                a.forecourtShiftIds.add(r.getShiftId());
            }
        }

        List<FuelDeliveryVariance> open = new ArrayList<>();
        for (FuelDeliveryVariance v : variances.findAllForTenant(tenantId)) if ("OPEN".equals(v.getStatus())) open.add(v);
        if (!open.isEmpty()) {
            Set<UUID> ids = new HashSet<>();
            for (FuelDeliveryVariance v : open) ids.add(v.getDeliveryId());
            Map<UUID, FuelDelivery> byId = new HashMap<>();
            for (FuelDelivery d : deliveries.findAllByIds(tenantId, ids)) byId.put(d.getId(), d);
            for (FuelDeliveryVariance v : open) {
                FuelDelivery d = byId.get(v.getDeliveryId());
                (d == null ? unassigned : owner(d, tankById, historicTanks, depotEntity, accs, unassigned)).openVariances++;
            }
        }

        List<Row> rows = new ArrayList<>();
        Acc total = new Acc();
        for (Map.Entry<UUID, Acc> e : accs.entrySet()) { rows.add(e.getValue().row(e.getKey(), names.get(e.getKey()), false, includeMoney)); total.addAll(e.getValue()); }
        if (unassigned.hasAnything()) { rows.add(unassigned.row(null, "Not assigned to a legal entity", true, includeMoney)); total.addAll(unassigned); }
        // Busiest first by litres, so the entities that matter lead the page.
        rows.sort(Comparator.comparing(Row::litres).reversed().thenComparing(Row::name));
        return new Report(from, to, prevFrom, prevTo, includeMoney, rows, total.row(null, "Whole group", false, includeMoney), left);
    }

    private static List<FuelDelivery> concat(List<FuelDelivery> a, List<FuelDelivery> b) { List<FuelDelivery> l = new ArrayList<>(a); l.addAll(b); return l; }

    private static Acc owner(FuelDelivery d, Map<UUID, FuelTank> tanks, Map<UUID, FuelTank> historic, Map<UUID, UUID> depotEntity, Map<UUID, Acc> accs, Acc unassigned) {
        FuelTank t = tanks.get(d.getTankId());
        if (t == null) t = historic.get(d.getTankId());
        return t == null ? unassigned : ownerOfTank(t, depotEntity, accs, unassigned);
    }

    private static Acc ownerOfTank(FuelTank t, Map<UUID, UUID> depotEntity, Map<UUID, Acc> accs, Acc unassigned) {
        UUID entity = t.getDepotId() == null ? null : depotEntity.get(t.getDepotId());
        Acc a = entity == null ? null : accs.get(entity);
        return a == null ? unassigned : a;
    }

    /** Adds a delivered load to the period it belongs to. Returns false when the load had to be left out (reversed, or no litres). */
    private static boolean add(Acc a, FuelDelivery d, boolean previousPeriod) {
        if (d.getReversedAt() != null || d.getLitresDelivered() == null) return false;
        BigDecimal litres = d.getLitresDelivered();
        BigDecimal revenue = d.getTotalAmount() != null ? d.getTotalAmount() : d.getPricePerLitre() == null ? BigDecimal.ZERO : litres.multiply(d.getPricePerLitre());
        BigDecimal margin = d.getCostPerLitreAtSale() == null ? null : revenue.subtract(litres.multiply(d.getCostPerLitreAtSale()));
        if (previousPeriod) {
            a.prevDeliveries++; a.prevLitres = a.prevLitres.add(litres); a.prevRevenue = a.prevRevenue.add(revenue);
            if (margin != null) a.prevMargin = a.prevMargin.add(margin);
        } else {
            a.deliveries++; a.litres = a.litres.add(litres); a.revenue = a.revenue.add(revenue);
            if (margin != null) a.margin = a.margin.add(margin); else a.withoutCost++;
        }
        return true;
    }

    private static BigDecimal m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private static final class Acc {
        long deliveries, prevDeliveries, withoutCost, tanksWithoutCost, openVariances;
        BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, margin = BigDecimal.ZERO,
                prevLitres = BigDecimal.ZERO, prevRevenue = BigDecimal.ZERO, prevMargin = BigDecimal.ZERO,
                stockLitres = BigDecimal.ZERO, stockValue = BigDecimal.ZERO, forecourtLitres = BigDecimal.ZERO, forecourtSales = BigDecimal.ZERO;
        Set<UUID> forecourtShiftIds = new HashSet<>();   // a set, so a shift that spans two entities still counts once in the group total

        boolean hasAnything() { return deliveries + prevDeliveries + openVariances > 0 || stockLitres.signum() != 0 || forecourtLitres.signum() != 0; }

        void addAll(Acc o) {
            deliveries += o.deliveries; prevDeliveries += o.prevDeliveries; withoutCost += o.withoutCost; tanksWithoutCost += o.tanksWithoutCost; openVariances += o.openVariances;
            litres = litres.add(o.litres); revenue = revenue.add(o.revenue); margin = margin.add(o.margin);
            prevLitres = prevLitres.add(o.prevLitres); prevRevenue = prevRevenue.add(o.prevRevenue); prevMargin = prevMargin.add(o.prevMargin);
            stockLitres = stockLitres.add(o.stockLitres); stockValue = stockValue.add(o.stockValue);
            forecourtLitres = forecourtLitres.add(o.forecourtLitres); forecourtSales = forecourtSales.add(o.forecourtSales); forecourtShiftIds.addAll(o.forecourtShiftIds);
        }

        Row row(UUID id, String name, boolean unassigned, boolean money) {
            return new Row(id, name == null ? "(removed entity)" : name, unassigned, deliveries, m(litres), money ? m(revenue) : null, money ? m(margin) : null, withoutCost,
                    prevDeliveries, m(prevLitres), money ? m(prevRevenue) : null, money ? m(prevMargin) : null,
                    m(stockLitres), money ? m(stockValue) : null, tanksWithoutCost, openVariances,
                    m(forecourtLitres), money ? m(forecourtSales) : null, forecourtShiftIds.size());
        }
    }
}
