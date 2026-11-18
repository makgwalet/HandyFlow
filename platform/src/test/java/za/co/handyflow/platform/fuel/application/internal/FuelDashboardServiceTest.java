package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.DipReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDispatchRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.dto.FuelDashboardSummary;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The dashboard's figures: whole month, South African calendar, limited to the tanks the caller may see. */
class FuelDashboardServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final ZoneId SA = ZoneId.of("Africa/Johannesburg");
    static final UUID TANK_A = UUID.randomUUID(), TANK_B = UUID.randomUUID();
    // Tuesday 17 November 2026, 12:00 in South Africa.
    static final Instant NOW = LocalDate.of(2026, 11, 17).atTime(12, 0).atZone(SA).toInstant();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    static BigDecimal d(String s) { return new BigDecimal(s); }
    static Instant at(int day, int hour) { return LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(SA).toInstant(); }
    static Object[] row(UUID tank, Instant when, String litres) { return new Object[]{tank, when, d(litres)}; }

    static FuelDelivery delivery(UUID tank, Instant scheduled, boolean inTransit) {
        FuelDelivery x = FuelDelivery.create(T, tank, UUID.randomUUID(), Map.of(), "diesel", d("5000"), d("22"), scheduled, "Driver", "GP 1");
        if (inTransit) x.dispatch("Driver", "GP 1");
        return x;
    }

    @Test @DisplayName("month litres, count and daily litres are summed over every row, with a late-night dispatch on the South African day")
    void monthFigures() {
        // 23:30 on the 3rd in South Africa is 21:30 UTC the same day; 01:00 on the 4th is 23:00 UTC on the 3rd: the SA day is what counts.
        var rows = List.of(row(TANK_A, at(1, 8), "1000"), row(TANK_A, at(3, 23), "500"), row(TANK_B, at(4, 1), "250"), row(TANK_A, at(17, 9), "100"));
        FuelDashboardSummary s = FuelDashboardService.build(TODAY, NOW, null, rows, List.of(), List.of(), List.of());
        assertEquals(0, d("1850").compareTo(s.monthLitres()));
        assertEquals(4, s.monthDispatches());
        assertEquals(17, s.dailyLitres().size());
        assertEquals(0, d("1000").compareTo(s.dailyLitres().get(0)));
        assertEquals(0, d("500").compareTo(s.dailyLitres().get(2)));
        assertEquals(0, d("250").compareTo(s.dailyLitres().get(3)));
        assertEquals(0, d("100").compareTo(s.dailyLitres().get(16)));
    }

    @Test @DisplayName("a user limited to some tanks only sees those tanks in every figure")
    void scoped() {
        var rows = List.of(row(TANK_A, at(2, 8), "1000"), row(TANK_B, at(2, 9), "700"));
        var open = List.of(delivery(TANK_A, at(18, 8), false), delivery(TANK_B, at(18, 9), true));
        FuelDashboardSummary s = FuelDashboardService.build(TODAY, NOW, Set.of(TANK_A), rows, List.of(TANK_A, TANK_B, TANK_B), List.of(TANK_B, TANK_A), open);
        assertEquals(0, d("1000").compareTo(s.monthLitres()));
        assertEquals(1, s.monthDispatches());
        assertEquals(1, s.monthReceipts());
        assertEquals(1, s.negativeVarianceReadings());
        assertEquals(1, s.openDeliveries());
        assertEquals(0, s.inTransitDeliveries());
    }

    @Test @DisplayName("a user with no tanks gets zeros and the repositories are not asked")
    void noTanks() {
        var disp = mock(FuelDispatchRepository.class); var rec = mock(FuelReceiptRepository.class); var dip = mock(DipReadingRepository.class); var del = mock(FuelDeliveryRepository.class); var cfg = mock(FuelSettingsService.class);
        FuelDashboardSummary s = new FuelDashboardService(disp, rec, dip, del, cfg).summary(T, Set.of(), NOW);
        assertEquals(0, s.monthDispatches());
        assertEquals(17, s.dailyLitres().size());
        assertTrue(s.nextDeliveries().isEmpty());
        verifyNoInteractions(disp, rec, dip, del, cfg);
    }

    @Test @DisplayName("the month runs from the 1st to the next 1st in South African time")
    void monthWindow() {
        var disp = mock(FuelDispatchRepository.class); var rec = mock(FuelReceiptRepository.class); var dip = mock(DipReadingRepository.class); var del = mock(FuelDeliveryRepository.class); var cfg = mock(FuelSettingsService.class);
        when(cfg.current(T)).thenReturn(za.co.handyflow.platform.fuel.domain.model.FuelSettings.forTenant(T));
        new FuelDashboardService(disp, rec, dip, del, cfg).summary(T, null, NOW);
        verify(disp).dashboardRows(T, LocalDate.of(2026, 11, 1).atStartOfDay(SA).toInstant(), LocalDate.of(2026, 12, 1).atStartOfDay(SA).toInstant());
        verify(dip).tankIdsWithVarianceBelow(eq(T), any(), any(), eq(new BigDecimal("-5")));
    }

    @Test @DisplayName("open deliveries: counts, in transit, overdue after the 120-minute grace (scheduled or on the road), and the next eight soonest")
    void deliveries() {
        List<FuelDelivery> open = new ArrayList<>();
        open.add(delivery(TANK_A, NOW.minusSeconds(30 * 3600), false));   // overdue
        open.add(delivery(TANK_A, NOW.minusSeconds(90 * 60), false));     // delayed, inside the grace: not overdue
        open.add(delivery(TANK_A, NOW.minusSeconds(48 * 3600), true));    // on the road and long past: overdue, as the push says
        for (int i = 0; i < 9; i++) open.add(delivery(TANK_A, NOW.plusSeconds((i + 1) * 3600L), false));
        FuelDashboardSummary s = FuelDashboardService.build(TODAY, NOW, null, List.of(), List.of(), List.of(), open);
        assertEquals(12, s.openDeliveries());
        assertEquals(1, s.inTransitDeliveries());
        assertEquals(2, s.overdueDeliveries());
        assertEquals(8, s.nextDeliveries().size());
        assertEquals("IN_TRANSIT", s.nextDeliveries().get(0).status());   // earliest scheduled first
        assertEquals("DIESEL", s.nextDeliveries().get(0).fuelType());
    }

    @Test @DisplayName("a dispatch dated outside the days so far is ignored in the daily list but still counted in the month")
    void futureDay() {
        var rows = List.<Object[]>of(row(TANK_A, at(25, 8), "300"));
        FuelDashboardSummary s = FuelDashboardService.build(TODAY, NOW, null, rows, List.of(), List.of(), List.of());
        assertEquals(0, d("300").compareTo(s.monthLitres()));
        assertTrue(s.dailyLitres().stream().allMatch(v -> v.signum() == 0));
    }
}
