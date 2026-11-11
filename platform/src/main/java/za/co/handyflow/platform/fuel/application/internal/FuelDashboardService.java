package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.DashboardResponse;
import za.co.handyflow.platform.fuel.dto.MarginResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Stage 4D: the manager's overview and delivery margin by customer. */
@Service
@RequiredArgsConstructor
public class FuelDashboardService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelTankRepository tanks;
    private final FuelReservationRepository reservations;
    private final FuelSalesOrderRepository orders;
    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerAccountRepository accounts;
    private final FuelComplianceItemRepository compliance;
    private final FuelPurchaseOrderRepository purchaseOrders;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public DashboardResponse overview(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        Instant now = Instant.now();

        Map<UUID, BigDecimal> reserved = new HashMap<>();
        for (Object[] row : reservations.sumLiveByTank(tenantId, now)) reserved.put((UUID) row[0], (BigDecimal) row[1]);
        Map<String, BigDecimal[]> byType = new TreeMap<>();
        int low = 0;
        for (FuelTank t : tanks.findAllActive(tenantId)) {
            BigDecimal[] sums = byType.computeIfAbsent(t.getFuelType(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(t.getCurrentLitres());
            sums[1] = sums[1].add(reserved.getOrDefault(t.getId(), BigDecimal.ZERO));
            sums[2] = sums[2].add(t.getCapacityLitres());
            if (t.isLow()) low++;
        }
        List<DashboardResponse.StockByType> stock = new ArrayList<>();
        byType.forEach((type, s) -> stock.add(new DashboardResponse.StockByType(type, s[0], s[1], s[0].subtract(s[1]).max(BigDecimal.ZERO), s[2])));

        List<FuelDelivery> notInvoiced = deliveries.findNotInvoiced(tenantId).stream().filter(d -> d.getReversedAt() == null).toList();
        BigDecimal notInvoicedValue = notInvoiced.stream().map(d -> d.getTotalAmount() == null ? BigDecimal.ZERO : d.getTotalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long failures = notInvoiced.stream().filter(d -> "FAILED".equals(d.getInvoiceState())).count();

        long onHold = accounts.findAllActive(tenantId).stream().filter(a -> a.isCreditHold()).count();

        Instant from = today.atStartOfDay(SAST).toInstant(), to = today.plusDays(1).atStartOfDay(SAST).toInstant();
        List<FuelDelivery> todays = deliveries.findScheduledBetween(tenantId, from, to);
        long unallocated = todays.stream().filter(d -> "SCHEDULED".equals(d.getStatus()) && !d.isAllocated()).count();

        return new DashboardResponse(today, stock, low,
                orders.countByStatus(tenantId, FuelSalesOrder.QUOTE), orders.countByStatus(tenantId, FuelSalesOrder.CONFIRMED),
                orders.countByStatus(tenantId, FuelSalesOrder.PART_DELIVERED),
                notInvoiced.size(), notInvoicedValue, failures, onHold,
                compliance.countExpired(tenantId, today), compliance.countExpiring(tenantId, today, today.plusDays(FuelComplianceItem.WARN_DAYS)),
                todays.size(), unallocated, purchaseOrders.countAwaitingDelivery(tenantId));
    }

    /** Margin by customer over the last {@code days} days. Reversed deliveries are left out; deliveries with no recorded cost are counted but not guessed at. */
    @Transactional(readOnly = true)
    public MarginResponse margin(TenantId tenantId, int days) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        List<FuelDelivery> rows = deliveries.findDeliveredBetween(tenantId, from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
        Map<UUID, Acc> acc = new LinkedHashMap<>();
        int noCost = 0;
        for (FuelDelivery d : rows) {
            if (d.getReversedAt() != null || d.getLitresDelivered() == null || d.getCustomerId() == null) continue;
            Acc a = acc.computeIfAbsent(d.getCustomerId(), k -> new Acc());
            BigDecimal revenue = d.getTotalAmount() != null ? d.getTotalAmount()
                    : d.getPricePerLitre() == null ? BigDecimal.ZERO : d.getLitresDelivered().multiply(d.getPricePerLitre());
            a.deliveries++; a.litres = a.litres.add(d.getLitresDelivered()); a.revenue = a.revenue.add(revenue);
            if (d.getCostPerLitreAtSale() == null) { noCost++; continue; }
            a.margin = a.margin.add(revenue.subtract(d.getLitresDelivered().multiply(d.getCostPerLitreAtSale())));
            a.costedLitres = a.costedLitres.add(d.getLitresDelivered());
        }
        List<MarginResponse.Row> out = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO, margin = BigDecimal.ZERO;
        for (Map.Entry<UUID, Acc> e : acc.entrySet()) {
            Acc a = e.getValue();
            String name = crm.findCustomerById(tenantId, e.getKey()).map(CustomerSummary::name).orElse("(customer removed)");
            BigDecimal perLitre = a.costedLitres.signum() == 0 ? null : a.margin.divide(a.costedLitres, 4, RoundingMode.HALF_UP);
            out.add(new MarginResponse.Row(e.getKey(), name, a.deliveries, a.litres, a.revenue.setScale(2, RoundingMode.HALF_UP), a.margin.setScale(2, RoundingMode.HALF_UP), perLitre));
            revenue = revenue.add(a.revenue); margin = margin.add(a.margin);
        }
        out.sort(Comparator.comparing(MarginResponse.Row::margin).reversed());
        return new MarginResponse(from, to, revenue.setScale(2, RoundingMode.HALF_UP), margin.setScale(2, RoundingMode.HALF_UP), noCost, out);
    }

    private static final class Acc {
        int deliveries; BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, margin = BigDecimal.ZERO, costedLitres = BigDecimal.ZERO;
    }
}
