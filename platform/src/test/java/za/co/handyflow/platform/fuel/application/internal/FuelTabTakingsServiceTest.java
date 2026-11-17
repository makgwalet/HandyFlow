package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.TabDtos.TakingsReport;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTabTakingsServiceTest {

    @Mock FuelTruckStopTabRepository tabs;
    @Mock FuelTruckStopTabLineRepository lines;
    @Mock FuelShiftReportClock clock;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelTruckStopTab tillTab(String method, Instant at) {
        FuelTruckStopTab t = FuelTruckStopTab.open(T, "GP 1", null, null, null);
        t.settleAtTill(method, null);
        ReflectionTestUtils.setField(t, "settledAt", at);
        return t;
    }

    FuelTruckStopTab accountTab(Instant at) {
        FuelTruckStopTab t = FuelTruckStopTab.open(T, "GP 2", null, UUID.randomUUID(), null);
        t.chargeToAccount(null);
        ReflectionTestUtils.setField(t, "settledAt", at);
        return t;
    }

    FuelTruckStopTabLine line(FuelTruckStopTab t, String qty, String price) {
        return FuelTruckStopTabLine.of(T, t.getId(), UUID.randomUUID(), "Service", bd(qty), bd(price), null);
    }

    @Test @DisplayName("till payments count on the day they were paid by method; account charges are shown apart and are not takings")
    void byDayAndMethod() {
        // 23:30 SAST on the 10th is still the 10th, even though it is 21:30 UTC.
        Instant tenthLate = LocalDate.of(2026, 11, 10).atTime(23, 30).atZone(SAST).toInstant();
        Instant eleventh = LocalDate.of(2026, 11, 11).atTime(9, 0).atZone(SAST).toInstant();
        FuelTruckStopTab cash = tillTab("CASH", tenthLate), eft = tillTab("EFT", eleventh), card = tillTab("CARD", eleventh), acct = accountTab(eleventh);
        when(clock.basis(T)).thenReturn(new FuelShiftReportClock.Basis(false, SAST));
        when(tabs.findSettledBetween(any(), any(), any())).thenReturn(List.of(cash, eft, card, acct));
        when(lines.findForTabs(any(), any())).thenReturn(List.of(line(cash, "2", "45"), line(eft, "1", "100"), line(card, "1", "80"), line(acct, "1", "500")));

        TakingsReport r = new FuelTabTakingsService(tabs, lines, clock).report(T, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

        assertEquals(2, r.days().size());
        assertEquals(LocalDate.of(2026, 11, 10), r.days().get(0).date());
        assertEquals(0, bd("90").compareTo(r.days().get(0).takingsExVat()));
        assertEquals(0, bd("180").compareTo(r.days().get(1).takingsExVat()));
        assertEquals(List.of("CARD", "EFT"), r.days().get(1).takings().stream().map(m -> m.method()).toList());
        assertEquals(1, r.days().get(1).chargedTabs());
        assertEquals(0, bd("500").compareTo(r.days().get(1).chargedExVat()));
        // The 500 charged to an account is not in takings.
        assertEquals(0, bd("270").compareTo(r.takingsExVat()));
        assertEquals(0, bd("500").compareTo(r.chargedExVat()));
    }

    @Test @DisplayName("the window is the company's local days, so the query spans start of from to start of the day after to")
    void window() {
        when(clock.basis(T)).thenReturn(new FuelShiftReportClock.Basis(false, SAST));
        when(tabs.findSettledBetween(any(), any(), any())).thenReturn(List.of());
        new FuelTabTakingsService(tabs, lines, clock).report(T, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        verify(tabs).findSettledBetween(T, LocalDate.of(2026, 11, 10).atStartOfDay(SAST).toInstant(), LocalDate.of(2026, 11, 11).atStartOfDay(SAST).toInstant());
    }

    @Test @DisplayName("an empty range gives an empty report, and bad ranges are refused")
    void ranges() {
        when(clock.basis(T)).thenReturn(new FuelShiftReportClock.Basis(false, SAST));
        when(tabs.findSettledBetween(any(), any(), any())).thenReturn(List.of());
        var s = new FuelTabTakingsService(tabs, lines, clock);
        assertTrue(s.report(T, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)).days().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> s.report(T, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 1)));
        assertThrows(IllegalArgumentException.class, () -> s.report(T, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1)));
    }

    @Test @DisplayName("EFT is a till method; a tab on an account still cannot be settled at the till")
    void eftMethod() {
        FuelTruckStopTab t = FuelTruckStopTab.open(T, "GP 3", null, null, null);
        t.settleAtTill("eft", null);
        assertEquals("EFT", t.getPaymentMethod());
        FuelTruckStopTab onAcct = FuelTruckStopTab.open(T, "GP 4", null, UUID.randomUUID(), null);
        assertThrows(IllegalStateException.class, () -> onAcct.settleAtTill("CASH", null));
    }
}
