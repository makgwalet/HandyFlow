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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Forecourt supply reconciliation: for each forecourt (a depot whose tanks have active pumps), how many litres came in during the period and from where:
 * the depot recorded as its supplier, any other depot, or a supplier receipt. Compared with litres sold on closed shifts.
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

    @Transactional(readOnly = true)
    public Reconciliation report(TenantId tenantId, int days, Set<UUID> allowedDepotIds) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        var fromI = from.atStartOfDay(SAST).toInstant();
        var toI = to.plusDays(1).atStartOfDay(SAST).toInstant();

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

        Map<UUID, Acc> acc = new HashMap<>();
        for (UUID f : forecourts) acc.put(f, new Acc());

        for (FuelTransfer t : transfers.findBetween(tenantId, fromI, toI)) {
            UUID in = tankDepot.get(t.getToTankId()), out = tankDepot.get(t.getFromTankId());
            Acc a = in == null ? null : acc.get(in);
            if (a == null || in.equals(out)) continue; // not a forecourt, or moved within the same depot
            if (out != null && out.equals(depotById.get(in).getSupplyingDepotId())) a.fromSupplierDepot = a.fromSupplierDepot.add(t.getLitres());
            else a.fromOtherDepots = a.fromOtherDepots.add(t.getLitres());
        }
        for (FuelReceipt r : receipts.findReceivedBetween(tenantId, fromI, toI)) {
            UUID in = tankDepot.get(r.getTankId());
            Acc a = in == null ? null : acc.get(in);
            if (a != null) a.fromSuppliers = a.fromSuppliers.add(r.getLitresReceived());
        }
        if (!forecourts.isEmpty()) {
            Set<UUID> ids = new HashSet<>();
            for (FuelShift s : shifts.findClosedSince(tenantId, fromI)) if (s.getClosedAt() != null && s.getClosedAt().isBefore(toI)) ids.add(s.getId());
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
            UUID sup = fd.getSupplyingDepotId();
            FuelDepot supDepot = sup == null ? null : depotById.get(sup);
            BigDecimal in = a.fromSupplierDepot.add(a.fromOtherDepots).add(a.fromSuppliers);
            BigDecimal share = in.signum() == 0 ? null : a.fromSupplierDepot.multiply(BigDecimal.valueOf(100)).divide(in, 1, RoundingMode.HALF_UP);
            rows.add(new Inbound(f, fd.getName(), supDepot == null ? null : supDepot.getId(), supDepot == null ? null : supDepot.getName(),
                    a.fromSupplierDepot, a.fromOtherDepots, a.fromSuppliers, in, share, a.sold));
            totalIn = totalIn.add(in); totalSold = totalSold.add(a.sold);
        }
        rows.sort(Comparator.comparing(Inbound::totalIn).reversed().thenComparing(Inbound::name));
        return new Reconciliation(from, to, rows, totalIn, totalSold);
    }

    private static final class Acc {
        BigDecimal fromSupplierDepot = BigDecimal.ZERO, fromOtherDepots = BigDecimal.ZERO, fromSuppliers = BigDecimal.ZERO, sold = BigDecimal.ZERO;
    }
}
