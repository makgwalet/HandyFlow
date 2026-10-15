package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthHireChargeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.MaintenanceRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthStatusEventRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.OperatorLogRepository;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.dto.ProfitabilityResponse;
import za.co.handyflow.platform.earthmoving.dto.UtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.MachineUtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.CustomerRevenueResponse;
import za.co.handyflow.platform.earthmoving.dto.DowntimeResponse;
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
    private final EarthStatusEventRepository statusEvents;
    private final CrmFacade crm;
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

    /** Hours worked per hour-metered machine in the dates. Every such machine is listed, including those that did not work. */
    @Transactional(readOnly = true)
    public MachineUtilisationResponse machineUtilisation(TenantId tenantId, LocalDate from, LocalDate to) {
        access.require(EarthmovingRules.READ, "view utilisation");
        ProfitRules.checkRange(from, to);
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        Map<UUID, EarthAsset> hourMachines = new LinkedHashMap<>();
        for (EarthAsset a : assets.findAllActive(tenantId, org.springframework.data.domain.Pageable.unpaged()))
            if (MeterRules.HOURS.equals(a.getMeterType()) && a.getStatus() != AssetStatus.RETIRED) hourMachines.put(a.getId(), a);
        Map<UUID, List<OperatorLog>> byAsset = new HashMap<>();
        for (OperatorLog l : operatorLogs.findCompletedBetween(tenantId, from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant()))
            if (hourMachines.containsKey(l.getAssetId())) byAsset.computeIfAbsent(l.getAssetId(), k -> new ArrayList<>()).add(l);
        List<MachineUtilisationResponse.Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (EarthAsset a : hourMachines.values()) {
            List<OperatorLog> logs = byAsset.getOrDefault(a.getId(), List.of());
            BigDecimal hours = logs.stream().map(OperatorLog::getHoursLogged).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, java.math.RoundingMode.HALF_UP);
            int worked = UtilisationRules.daysWorked(logs.stream().map(OperatorLog::getStartedAt).toList(), ZONE);
            total = total.add(hours);
            rows.add(new MachineUtilisationResponse.Row(a.getId(), label(a), logs.size(), hours, worked, UtilisationRules.percentOfDays(worked, days)));
        }
        rows.sort(Comparator.comparing(MachineUtilisationResponse.Row::hours).reversed().thenComparing(MachineUtilisationResponse.Row::machine));
        return new MachineUtilisationResponse(from, to, (int) days, total, rows);
    }

    /** Hire revenue per customer (not voided, by the period the hire starts), largest first. Administrator only: it is financial. */
    @Transactional(readOnly = true)
    public CustomerRevenueResponse customerRevenue(TenantId tenantId, LocalDate from, LocalDate to) {
        access.require(EarthmovingRules.ADMIN, "view hire revenue by customer");
        ProfitRules.checkRange(from, to);
        Map<UUID, Acc> byCustomer = new LinkedHashMap<>();
        Acc all = new Acc();
        for (EarthHireCharge c : charges.findRevenueBetween(tenantId, from, to)) {
            Acc a = byCustomer.computeIfAbsent(c.getCustomerId(), k -> new Acc());
            a.charges++; all.charges++;
            a.revenue = a.revenue.add(c.getAmount()); all.revenue = all.revenue.add(c.getAmount());
            if (HireRules.INVOICED.equals(c.getStatus())) { a.invoiced = a.invoiced.add(c.getAmount()); all.invoiced = all.invoiced.add(c.getAmount()); }
        }
        List<CustomerRevenueResponse.Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, Acc> e : byCustomer.entrySet()) {
            String name = e.getKey() == null ? "No customer linked"
                    : crm.findCustomerById(tenantId, e.getKey()).map(CustomerSummary::name).orElse("A removed customer");
            rows.add(customerRow(e.getKey(), name, e.getValue(), all.revenue));
        }
        rows.sort(Comparator.comparing(CustomerRevenueResponse.Row::revenue).reversed().thenComparing(CustomerRevenueResponse.Row::customer));
        return new CustomerRevenueResponse(from, to, customerRow(null, "All customers", all, all.revenue), rows);
    }

    private static CustomerRevenueResponse.Row customerRow(UUID id, String name, Acc a, BigDecimal allRevenue) {
        BigDecimal share = allRevenue.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : a.revenue.multiply(BigDecimal.valueOf(100)).divide(allRevenue, 1, java.math.RoundingMode.HALF_UP);
        return new CustomerRevenueResponse.Row(id, name, a.charges, a.revenue, a.invoiced, a.revenue.subtract(a.invoiced), share);
    }

    /** Time in breakdown and in maintenance per machine, from the status changes on record. */
    @Transactional(readOnly = true)
    public DowntimeResponse downtime(TenantId tenantId, LocalDate from, LocalDate to) {
        access.require(EarthmovingRules.READ, "view downtime");
        ProfitRules.checkRange(from, to);
        java.time.Instant start = from.atStartOfDay(ZONE).toInstant();
        java.time.Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        java.time.Instant now = java.time.Instant.now();
        Map<UUID, List<DowntimeRules.Change>> byAsset = new LinkedHashMap<>();
        for (EarthStatusEvent e : statusEvents.findUpTo(tenantId, end))
            byAsset.computeIfAbsent(e.getAssetId(), k -> new ArrayList<>()).add(new DowntimeRules.Change(e.getHappenedAt(), e.getToStatus()));
        List<DowntimeResponse.Row> rows = new ArrayList<>();
        long allBreakdown = 0, allMaintenance = 0;
        int allBreakdowns = 0;
        for (EarthAsset a : assets.findAllActive(tenantId, org.springframework.data.domain.Pageable.unpaged())) {
            if (a.getStatus() == AssetStatus.RETIRED) continue;
            DowntimeRules.Result r = DowntimeRules.compute(byAsset.getOrDefault(a.getId(), List.of()), start, end, now);
            if (r.totalSeconds() == 0 && r.breakdowns() == 0) continue;
            allBreakdown += r.breakdownSeconds(); allMaintenance += r.maintenanceSeconds(); allBreakdowns += r.breakdowns();
            rows.add(downtimeRow(a.getId(), label(a), r, start, end, now));
        }
        rows.sort(Comparator.comparing(DowntimeResponse.Row::totalHours).reversed().thenComparing(DowntimeResponse.Row::machine));
        return new DowntimeResponse(from, to, new DowntimeResponse.Row(null, "All machines", allBreakdowns, hours(allBreakdown), hours(allMaintenance),
                hours(allBreakdown + allMaintenance), null), rows);
    }

    private static DowntimeResponse.Row downtimeRow(UUID id, String machine, DowntimeRules.Result r, java.time.Instant start, java.time.Instant end, java.time.Instant now) {
        return new DowntimeResponse.Row(id, machine, r.breakdowns(), hours(r.breakdownSeconds()), hours(r.maintenanceSeconds()), hours(r.totalSeconds()),
                DowntimeRules.availabilityPercent(r.totalSeconds(), start, end, now));
    }

    private static BigDecimal hours(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 1, java.math.RoundingMode.HALF_UP);
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
