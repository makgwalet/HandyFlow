package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.TabDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Truck stop takings by day and by payment method.
 * <p>
 * A tab paid at the till is takings on the day it was PAID, by the method used. A tab charged to an account is shown separately on the day it was charged:
 * it is billed by an invoice and collected there, so it is never counted as takings (and so never as revenue twice). Voided tabs are left out.
 * Amounts are VAT-exclusive, like the tab lines. Read only: nothing here changes the forecourt shifts, whose takings are checked against pump meters.
 */
@Service
@RequiredArgsConstructor
public class FuelTabTakingsService {

    static final int MAX_DAYS = 93;
    static final List<String> ORDER = List.of("CASH", "CARD", "EFT", "OTHER");

    private final FuelTruckStopTabRepository tabs;
    private final FuelTruckStopTabLineRepository lines;
    private final FuelShiftReportClock clock;

    @Transactional(readOnly = true)
    public TakingsReport report(TenantId tenant, LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Give a from and a to date.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date is before the start date.");
        if (from.plusDays(MAX_DAYS).isBefore(to)) throw new IllegalArgumentException("Choose at most " + MAX_DAYS + " days at a time.");
        ZoneId zone = clock.basis(tenant).zone();
        List<FuelTruckStopTab> settled = tabs.findSettledBetween(tenant, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
        Map<UUID, BigDecimal> totals = settled.isEmpty() ? Map.of() : lines.findForTabs(tenant, settled.stream().map(FuelTruckStopTab::getId).toList()).stream()
                .collect(Collectors.groupingBy(FuelTruckStopTabLine::getTabId, Collectors.reducing(BigDecimal.ZERO, FuelTruckStopTabLine::amount, BigDecimal::add)));

        Map<LocalDate, Map<String, BigDecimal>> amount = new TreeMap<>();
        Map<LocalDate, Map<String, Integer>> count = new TreeMap<>();
        for (FuelTruckStopTab t : settled) {
            LocalDate day = t.getSettledAt().atZone(zone).toLocalDate();
            String m = t.getPaymentMethod() == null ? "OTHER" : t.getPaymentMethod();
            BigDecimal a = totals.getOrDefault(t.getId(), BigDecimal.ZERO);
            amount.computeIfAbsent(day, d -> new HashMap<>()).merge(m, a, BigDecimal::add);
            count.computeIfAbsent(day, d -> new HashMap<>()).merge(m, 1, Integer::sum);
        }
        List<TakingsDay> days = new ArrayList<>();
        Map<String, BigDecimal> grandAmount = new HashMap<>();
        Map<String, Integer> grandCount = new HashMap<>();
        BigDecimal takingsAll = BigDecimal.ZERO, chargedAll = BigDecimal.ZERO;
        int chargedTabsAll = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, BigDecimal> am = amount.getOrDefault(d, Map.of());
            Map<String, Integer> ct = count.getOrDefault(d, Map.of());
            List<MethodTakings> rows = new ArrayList<>();
            BigDecimal dayTakings = BigDecimal.ZERO;
            for (String m : ORDER) {
                if (!am.containsKey(m)) continue;
                rows.add(new MethodTakings(m, ct.get(m), am.get(m)));
                dayTakings = dayTakings.add(am.get(m));
                grandAmount.merge(m, am.get(m), BigDecimal::add);
                grandCount.merge(m, ct.get(m), Integer::sum);
            }
            BigDecimal charged = am.getOrDefault("ACCOUNT", BigDecimal.ZERO);
            int chargedTabs = ct.getOrDefault("ACCOUNT", 0);
            if (!rows.isEmpty() || chargedTabs > 0) days.add(new TakingsDay(d, rows, dayTakings, chargedTabs, charged));
            takingsAll = takingsAll.add(dayTakings); chargedAll = chargedAll.add(charged); chargedTabsAll += chargedTabs;
        }
        List<MethodTakings> grand = new ArrayList<>();
        for (String m : ORDER) if (grandAmount.containsKey(m)) grand.add(new MethodTakings(m, grandCount.get(m), grandAmount.get(m)));
        return new TakingsReport(from, to, zone.getId(), days, grand, takingsAll, chargedTabsAll, chargedAll);
    }
}
