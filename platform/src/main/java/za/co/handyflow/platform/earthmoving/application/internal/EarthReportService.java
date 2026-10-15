package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthHireChargeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.MaintenanceRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.OperatorLogRepository;
import za.co.handyflow.platform.earthmoving.dto.ProfitabilityResponse;
import za.co.handyflow.platform.earthmoving.dto.UtilisationResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Reports across the fleet. Financial, so they need administrator authority. */
@Service
@RequiredArgsConstructor
public class EarthReportService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final EarthHireChargeRepository charges;
    private final MaintenanceRepository maintenance;
    private final EarthAssetRepository assets;
    private final OperatorLogRepository operatorLogs;
    private final EarthmovingAccess access;

    private static final class Acc {
        int charges; BigDecimal revenue = BigDecimal.ZERO; BigDecimal invoiced = BigDecimal.ZERO; BigDecimal cost = BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse profitability(TenantId tenantId, LocalDate from, LocalDate to) {
        access.require(EarthmovingRules.ADMIN, "view the profitability report");
        ProfitRules.checkRange(from, to);

        Map<UUID, Acc> byAsset = new LinkedHashMap<>();
        for (EarthHireCharge c : charges.findRevenueBetween(tenantId, from, to)) {
            Acc a = byAsset.computeIfAbsent(c.getAssetId(), k -> new Acc());
            a.charges++;
            a.revenue = a.revenue.add(c.getAmount());
            if (HireRules.INVOICED.equals(c.getStatus())) a.invoiced = a.invoiced.add(c.getAmount());
        }
        for (MaintenanceRecord m : maintenance.findCostedBetween(tenantId,
                from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant())) {
            Acc a = byAsset.computeIfAbsent(m.getAssetId(), k -> new Acc());
            a.cost = a.cost.add(m.getCost());
        }

        List<ProfitabilityResponse.Row> rows = new ArrayList<>();
        Acc all = new Acc();
        for (Map.Entry<UUID, Acc> e : byAsset.entrySet()) {
            Acc a = e.getValue();
            all.charges += a.charges; all.revenue = all.revenue.add(a.revenue); all.invoiced = all.invoiced.add(a.invoiced); all.cost = all.cost.add(a.cost);
            rows.add(row(e.getKey(), assets.findActiveById(tenantId, e.getKey()).map(EarthReportService::label).orElse("A removed machine"), a));
        }
        rows.sort(Comparator.comparing(ProfitabilityResponse.Row::margin).reversed().thenComparing(ProfitabilityResponse.Row::machine));
        return new ProfitabilityResponse(from, to, row(null, "All machines", all), rows);
    }

    /** Hours worked per day over the last {@code days} days on hour-metered machines. Anyone who can read the module may see it. */
    @Transactional(readOnly = true)
    public UtilisationResponse utilisation(TenantId tenantId, int days) {
        access.require(EarthmovingRules.READ, "view utilisation");
        UtilisationRules.checkDays(days);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(days - 1L);
        Set<UUID> hourMachines = new HashSet<>();
        for (EarthAsset a : assets.findAllActive(tenantId, org.springframework.data.domain.Pageable.unpaged()))
            if (MeterRules.HOURS.equals(a.getMeterType())) hourMachines.add(a.getId());
        List<UtilisationRules.Shift> shifts = new ArrayList<>();
        for (OperatorLog l : operatorLogs.findCompletedBetween(tenantId, first.atStartOfDay(ZONE).toInstant(), today.plusDays(1).atStartOfDay(ZONE).toInstant()))
            if (hourMachines.contains(l.getAssetId())) shifts.add(new UtilisationRules.Shift(l.getStartedAt(), l.getHoursLogged()));
        List<BigDecimal> perDay = UtilisationRules.perDay(shifts, today, days, ZONE);
        BigDecimal total = perDay.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UtilisationResponse(first, today, hourMachines.size(), total, perDay);
    }

    private static ProfitabilityResponse.Row row(UUID id, String machine, Acc a) {
        BigDecimal margin = ProfitRules.margin(a.revenue, a.cost);
        return new ProfitabilityResponse.Row(id, machine, a.charges, a.revenue, a.invoiced, a.revenue.subtract(a.invoiced), a.cost, margin,
                ProfitRules.marginPercent(a.revenue, margin));
    }

    private static String label(EarthAsset a) {
        return a.getFleetNumber() != null && !a.getFleetNumber().isBlank() ? a.getFleetNumber() + " " + a.getName() : a.getName();
    }
}
