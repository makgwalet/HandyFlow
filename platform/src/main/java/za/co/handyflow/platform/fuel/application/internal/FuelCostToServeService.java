package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.CostToServeResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Cost-to-serve by customer. What it measures, and what it does not:
 * <ul>
 *   <li>Driver cost = hours x hourly rate. Hours are loaded-to-delivered when both times exist and are plausible (under 16 h),
 *       otherwise the tenant's "typical trip hours" (flagged as estimated). The drive back is not timed.</li>
 *   <li>Vehicle cost = round-trip km (twice the site's one-way distance) x per-km rate. A delivery to a site with no distance
 *       gets no vehicle cost and is flagged, so the figure is a floor, not a guess.</li>
 *   <li>No GPS, odometer or fuel-burn data is used.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FuelCostToServeService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final BigDecimal MAX_MEASURED_HOURS = new BigDecimal("16");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerSiteRepository sites;
    private final FuelSettingsService settingsService;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public CostToServeResponse costToServe(TenantId tenantId, int days) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        FuelSettings s = settingsService.current(tenantId);
        List<FuelDelivery> rows = deliveries.findDeliveredBetween(tenantId, from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());

        Set<UUID> siteIds = new HashSet<>();
        for (FuelDelivery d : rows) if (d.getSiteId() != null) siteIds.add(d.getSiteId());
        Map<UUID, BigDecimal> distance = new HashMap<>();
        // Includes sites removed since: their deliveries still happened.
        for (FuelCustomerSite site : sites.findAllById(siteIds)) {
            if (site.getDistanceKm() != null && tenantId.equals(site.getTenantId())) distance.put(site.getId(), site.getDistanceKm());
        }

        Map<UUID, Acc> acc = new LinkedHashMap<>();
        int noCost = 0, count = 0;
        for (FuelDelivery d : rows) {
            if (d.getReversedAt() != null || d.getLitresDelivered() == null || d.getCustomerId() == null) continue;
            if (d.getCostPerLitreAtSale() == null) { noCost++; continue; }
            Acc a = acc.computeIfAbsent(d.getCustomerId(), k -> new Acc());
            BigDecimal litres = d.getLitresDelivered();
            BigDecimal revenue = d.getTotalAmount() != null ? d.getTotalAmount()
                    : d.getPricePerLitre() == null ? BigDecimal.ZERO : litres.multiply(d.getPricePerLitre());
            BigDecimal hours = measuredHours(d);
            if (hours == null) { hours = s.getDefaultTripHours(); a.estimatedHours++; }
            BigDecimal km = d.getSiteId() == null ? null : distance.get(d.getSiteId());
            if (km == null) a.noDistance++;

            a.deliveries++; count++;
            a.litres = a.litres.add(litres);
            a.revenue = a.revenue.add(revenue);
            a.margin = a.margin.add(revenue.subtract(litres.multiply(d.getCostPerLitreAtSale())));
            a.driver = a.driver.add(hours.multiply(s.getDriverCostPerHour()));
            if (km != null) a.vehicle = a.vehicle.add(km.multiply(BigDecimal.valueOf(2)).multiply(s.getVehicleCostPerKm()));
            a.fixed = a.fixed.add(s.getFixedCostPerDelivery());
            a.overhead = a.overhead.add(revenue.multiply(s.getOverheadPctOfRevenue()).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        }

        List<CostToServeResponse.Row> out = new ArrayList<>();
        Acc total = new Acc();
        for (Map.Entry<UUID, Acc> e : acc.entrySet()) {
            Acc a = e.getValue();
            String name = crm.findCustomerById(tenantId, e.getKey()).map(CustomerSummary::name).orElse("(customer removed)");
            BigDecimal net = a.margin.subtract(a.driver).subtract(a.vehicle).subtract(a.fixed).subtract(a.overhead);
            BigDecimal perLitre = a.litres.signum() == 0 ? null : net.divide(a.litres, 4, RoundingMode.HALF_UP);
            out.add(new CostToServeResponse.Row(e.getKey(), name, a.deliveries, a.litres, m(a.revenue), m(a.margin), m(a.driver), m(a.vehicle),
                    m(a.fixed), m(a.overhead), m(net), perLitre, a.noDistance, a.estimatedHours));
            total.add(a);
        }
        out.sort(Comparator.comparing(CostToServeResponse.Row::netMargin).reversed());
        BigDecimal totalNet = total.margin.subtract(total.driver).subtract(total.vehicle).subtract(total.fixed).subtract(total.overhead);
        return new CostToServeResponse(from, to, !s.ratesUnset(), count, noCost, total.noDistance, total.estimatedHours,
                m(total.revenue), m(total.margin), m(total.driver), m(total.vehicle), m(total.fixed), m(total.overhead), m(totalNet), out);
    }

    /** Loaded-to-delivered hours, or null when either time is missing or the gap is not plausible for one trip. */
    static BigDecimal measuredHours(FuelDelivery d) {
        if (d.getLoadedAt() == null || d.getDeliveredAt() == null) return null;
        long minutes = Duration.between(d.getLoadedAt(), d.getDeliveredAt()).toMinutes();
        if (minutes <= 0) return null;
        BigDecimal h = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return h.compareTo(MAX_MEASURED_HOURS) > 0 ? null : h;
    }

    private static BigDecimal m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private static final class Acc {
        int deliveries, noDistance, estimatedHours;
        BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, margin = BigDecimal.ZERO, driver = BigDecimal.ZERO,
                vehicle = BigDecimal.ZERO, fixed = BigDecimal.ZERO, overhead = BigDecimal.ZERO;
        void add(Acc o) {
            noDistance += o.noDistance; estimatedHours += o.estimatedHours;
            revenue = revenue.add(o.revenue); margin = margin.add(o.margin); driver = driver.add(o.driver);
            vehicle = vehicle.add(o.vehicle); fixed = fixed.add(o.fixed); overhead = overhead.add(o.overhead);
        }
    }
}
