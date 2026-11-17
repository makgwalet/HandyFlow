package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Inbound;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Reconciliation;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Forecourt supply reconciliation: for each forecourt (a depot whose tanks have active pumps), how many litres came in during the period and from where:
 * the depot recorded as its supplier (or the depots in its saved supply split), any other depot, or a supplier receipt. Compared with litres sold on closed shifts.
 * A transfer is one record, so this is not a sent-versus-received check; it shows whether the supply actually follows the supplier on record.
 * Transfers between tanks of the same depot are ignored. Read-only; litres only.
 */
@Service
@RequiredArgsConstructor
public class FuelForecourtSupplyReconciliationService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelTransferRepository transfers;
    private final FuelReceiptRepository receipts;
    private final FuelPumpRepository pumps;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;
    private final FuelDepotSupplyShareRepository supplyShares;
    private final FuelShiftReportClock clock;

    @Transactional(readOnly = true)
    public Reconciliation report(TenantId tenantId, int days, Set<UUID> allowedDepotIds) {
        int span = Math.max(1, Math.min(days, 366));
        var basis = clock.basis(tenantId);
        LocalDate to = LocalDate.now(basis.zone()), from = to.minusDays(span - 1L);
        var fromI = from.atStartOfDay(basis.zone()).toInstant();
        var toI = to.plusDays(1).atStartOfDay(basis.zone()).toInstant();

        Map<UUID, FuelDepot> depotById = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) depotById.put(d.getId(), d);
        Map<UUID, UUID> tankDepot = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankDepot.put(t.getId(), t.getDepotId());

        Set<UUID> pumpTanks = new HashSet<>();
        for (FuelPump p : pumps.findAllActive(tenantId)) if (p.getTankId() != null) pumpTanks.add(p.getTankId());
        Set<UUID> forecourts = new LinkedHashSet<>();
        for (UUID tankId : pumpTanks) {
            UUID d = tankDepot.get(tankId);
            if (d != null && depotById.containsKey(d) && (allowedDepotIds == null || allowedDepotIds.contains(d))) forecourts.add(d);
        }

        // The depots that count as a forecourt's supplier: its saved split if it has one, otherwise its single supplier.
        Map<UUID, Set<UUID>> suppliersOf = new HashMap<>();
        Map<UUID, List<FuelDepotSupplyShare>> splits = new HashMap<>();
        for (FuelDepotSupplyShare sh : supplyShares.findAllForTenant(tenantId)) {
            if (!depotById.containsKey(sh.getSupplyingDepotId())) continue;
            suppliersOf.computeIfAbsent(sh.getDepotId(), k -> new HashSet<>()).add(sh.getSupplyingDepotId());
            splits.computeIfAbsent(sh.getDepotId(), k -> new ArrayList<>()).add(sh);
        }
        for (UUID f : forecourts) if (!suppliersOf.containsKey(f)) {
            UUID one = depotById.get(f).getSupplyingDepotId();
            if (one != null && depotById.containsKey(one)) suppliersOf.put(f, Set.of(one));
        }

        Map<UUID, Acc> acc = new HashMap<>();
        for (UUID f : forecourts) acc.put(f, new Acc());

        for (FuelTransfer t : transfers.findBetween(tenantId, fromI, toI)) {
            UUID in = tankDepot.get(t.getToTankId()), out = tankDepot.get(t.getFromTankId());
            Acc a = in == null ? null : acc.get(in);
            if (a == null || in.equals(out)) continue; // not a forecourt, or moved within the same depot
            if (out != null && suppliersOf.getOrDefault(in, Set.of()).contains(out)) a.fromSupplierDepot = a.fromSupplierDepot.add(t.getLitres());
            else a.fromOtherDepots = a.fromOtherDepots.add(t.getLitres());
        }
        for (FuelReceipt r : receipts.findReceivedBetween(tenantId, fromI, toI)) {
            UUID in = tankDepot.get(r.getTankId());
            Acc a = in == null ? null : acc.get(in);
            if (a != null) a.fromSuppliers = a.fromSuppliers.add(r.getLitresReceived());
        }
        List<FuelShift> counted = new ArrayList<>();
        if (!forecourts.isEmpty()) {
            Set<UUID> ids = new HashSet<>();
            for (FuelShift s : shifts.findClosedSince(tenantId, fromI)) {
                Instant at = basis.closedFor(s);
                if (at != null && !at.isBefore(fromI) && at.isBefore(toI)) { ids.add(s.getId()); counted.add(s); }
            }
            if (!ids.isEmpty()) {
                for (FuelShiftReading r : readings.findByShifts(tenantId, ids)) {
                    if (r.getLitresSold() == null) continue;
                    UUID d = tankDepot.get(r.getTankId());
                    Acc a = d == null ? null : acc.get(d);
                    if (a != null) a.sold = a.sold.add(r.getLitresSold());
                }
            }
        }

        List<Inbound> rows = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO, totalSold = BigDecimal.ZERO;
        for (UUID f : forecourts) {
            Acc a = acc.get(f);
            FuelDepot fd = depotById.get(f);
            List<FuelDepotSupplyShare> split = splits.get(f);
            UUID sup = fd.getSupplyingDepotId();
            FuelDepot supDepot = sup == null ? null : depotById.get(sup);
            String supName = supDepot == null ? null : supDepot.getName();
            if (split != null && split.size() > 1) {
                split.sort(Comparator.comparing(FuelDepotSupplyShare::getPercentage).reversed());
                StringBuilder sb = new StringBuilder();
                for (FuelDepotSupplyShare sh : split) sb.append(sb.length() == 0 ? "" : ", ").append(depotById.get(sh.getSupplyingDepotId()).getName()).append(" ").append(sh.getPercentage().stripTrailingZeros().toPlainString()).append("%");
                supName = sb.toString();
            } else if (split != null && split.size() == 1) {
                supDepot = depotById.get(split.get(0).getSupplyingDepotId()); supName = supDepot.getName();
            }
            BigDecimal in = a.fromSupplierDepot.add(a.fromOtherDepots).add(a.fromSuppliers);
            BigDecimal share = in.signum() == 0 ? null : a.fromSupplierDepot.multiply(BigDecimal.valueOf(100)).divide(in, 1, RoundingMode.HALF_UP);
            rows.add(new Inbound(f, fd.getName(), supDepot == null && split == null ? null : (supDepot != null ? supDepot.getId() : split.get(0).getSupplyingDepotId()), supName,
                    a.fromSupplierDepot, a.fromOtherDepots, a.fromSuppliers, in, share, a.sold));
            totalIn = totalIn.add(in); totalSold = totalSold.add(a.sold);
        }
        rows.sort(Comparator.comparing(Inbound::totalIn).reversed().thenComparing(Inbound::name));
        return new Reconciliation(from, to, rows, totalIn, totalSold, clock.describe(tenantId, basis, counted));
    }

    private static final class Acc {
        BigDecimal fromSupplierDepot = BigDecimal.ZERO, fromOtherDepots = BigDecimal.ZERO, fromSuppliers = BigDecimal.ZERO, sold = BigDecimal.ZERO;
    }
}
