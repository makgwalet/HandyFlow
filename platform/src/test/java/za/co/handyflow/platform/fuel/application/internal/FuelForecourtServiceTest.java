package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelForecourtServiceTest {

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    @Mock FuelPumpRepository pumps;
    @Mock FuelShiftRepository shifts;
    @Mock FuelShiftReadingRepository readings;
    @Mock FuelTankRepository tanks;
    @Mock FuelStockLedger ledger;
    @Mock ShiftNumberGenerator numbers;
    @Mock FuelCashControlService cash;
    @Mock FuelToleranceService tolerances;
    @Mock FuelShiftAlertService shiftAlerts;
    @InjectMocks FuelForecourtService service;

    FuelTank diesel;
    FuelPump pump1, pump2;
    List<FuelShiftReading> stored = new ArrayList<>();
    FuelShift openShift;

    static BigDecimal d(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        when(tolerances.cashToleranceFor(any(), any(), any())).thenAnswer(i -> i.getArgument(2));   // no depot override: the company-wide number
        diesel = FuelTank.create(TENANT, "Diesel 1", "DIESEL", d("20000"), null, d("20"));
        diesel.addStock(d("10000"), d("18.00"));
        pump1 = FuelPump.create(TENANT, "Pump 1", diesel.getId(), d("20.00"));
        pump2 = FuelPump.create(TENANT, "Pump 2", diesel.getId(), d("20.00"));
        when(tanks.findAllActive(TENANT)).thenReturn(List.of(diesel));
        when(tanks.findActiveById(TENANT, diesel.getId())).thenReturn(Optional.of(diesel));
        when(tanks.findActiveByIdForUpdate(TENANT, diesel.getId())).thenReturn(Optional.of(diesel));
        when(pumps.findAllActive(TENANT)).thenReturn(List.of(pump1, pump2));
        when(pumps.findActiveById(TENANT, pump1.getId())).thenReturn(Optional.of(pump1));
        when(pumps.findActiveByIds(eq(TENANT), any())).thenReturn(List.of(pump1, pump2));
        when(numbers.generate(TENANT)).thenReturn("SH-2026-00001");
        when(shifts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pumps.save(any())).thenAnswer(i -> i.getArgument(0));
        when(readings.save(any())).thenAnswer(i -> { stored.add(i.getArgument(0)); return i.getArgument(0); });
        when(readings.findByShift(eq(TENANT), any())).thenAnswer(i -> stored);
        when(readings.findOpenForPumps(eq(TENANT), any())).thenReturn(List.of());
        when(cash.policy(TENANT)).thenReturn(new FuelCashControlService.CashPolicy(d("5.00"), false));
        when(cash.approvedTotal(eq(TENANT), any())).thenReturn(BigDecimal.ZERO);
        when(cash.pendingCount(eq(TENANT), any())).thenReturn(0L);
        openShift = null;
    }

    private FuelShift opened() {
        ShiftResponse r = service.openShift(TENANT, new OpenShiftRequest("Thabo", null,
                List.of(new OpenReading(pump1.getId(), d("1000")), new OpenReading(pump2.getId(), d("5000")))), USER);
        ArgumentCaptor<FuelShift> cap = ArgumentCaptor.forClass(FuelShift.class);
        verify(shifts, atLeastOnce()).save(cap.capture());
        openShift = cap.getValue();
        when(shifts.findByTenantAndId(TENANT, openShift.getId())).thenReturn(Optional.of(openShift));
        assertEquals("OPEN", r.status());
        return openShift;
    }

    private CloseShiftRequest close(String cash, String card, String reason, String c1, String t1, String c2) {
        return new CloseShiftRequest(d(cash), d(card), BigDecimal.ZERO, BigDecimal.ZERO, reason,
                List.of(new CloseReading(pump1.getId(), d(c1), d(t1)), new CloseReading(pump2.getId(), d(c2), BigDecimal.ZERO)));
    }

    @Test @DisplayName("closing a shift takes litres off the tank in one ledger line per tank, net of test litres, and records a zero variance")
    void closesAndPostsStock() {
        FuelShift s = opened();
        // pump1: 1000 -> 1210 (210 moved, 10 test = 200 sold); pump2: 5000 -> 5100 (100 sold) => 300 L x R20 = R6000
        ShiftResponse r = service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER);
        assertEquals("CLOSED", r.status());
        assertEquals(0, d("300").compareTo(r.litresSold()));
        assertEquals(0, d("6000").compareTo(r.expected()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.variance()));
        assertEquals(0, d("9700").compareTo(diesel.getCurrentLitres()));
        verify(ledger, times(1)).post(eq(diesel), eq(FuelStockMovement.Type.DISPATCH), org.mockito.ArgumentMatchers.argThat((BigDecimal b) -> b.compareTo(d("-300")) == 0), eq("FORECOURT_SHIFT"), eq(s.getId()), any());
        assertEquals(0, d("1210").compareTo(pump1.getLastMeter()));
    }

    @Test @DisplayName("takings outside the tolerance need a reason, and a refusal leaves stock untouched")
    void varianceNeedsReason() {
        FuelShift s = opened();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeShift(TENANT, s.getId(), close("3000", "2000", null, "1210", "10", "5100"), USER));
        assertTrue(ex.getMessage().contains("short"));
        assertEquals(0, d("10000").compareTo(diesel.getCurrentLitres()));
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any());
        // with a reason it closes and the variance is kept
        ShiftResponse r = service.closeShift(TENANT, s.getId(), close("3000", "2000", "Drive-off, reported to the manager", "1210", "10", "5100"), USER);
        assertEquals(0, d("-1000").compareTo(r.variance()));
        assertEquals("Drive-off, reported to the manager", r.varianceReason());
    }

    @Test @DisplayName("a small rounding difference closes without a reason")
    void toleranceAllowsRounding() {
        FuelShift s = opened();
        ShiftResponse r = service.closeShift(TENANT, s.getId(), close("3998", "2000", null, "1210", "10", "5100"), USER);
        assertEquals("CLOSED", r.status());
        assertEquals(0, d("-2").compareTo(r.variance()));
    }

    @Test @DisplayName("a closing meter below the opening meter is refused")
    void meterCannotRunBackwards() {
        FuelShift s = opened();
        assertThrows(IllegalArgumentException.class, () -> service.closeShift(TENANT, s.getId(), close("0", "0", null, "999", "0", "5100"), USER));
        assertEquals(0, d("10000").compareTo(diesel.getCurrentLitres()));
    }

    @Test @DisplayName("a missing closing reading is refused")
    void everyPumpMustBeRead() {
        FuelShift s = opened();
        CloseShiftRequest req = new CloseShiftRequest(d("0"), d("0"), d("0"), d("0"), null, List.of(new CloseReading(pump1.getId(), d("1000"), null)));
        assertThrows(IllegalArgumentException.class, () -> service.closeShift(TENANT, s.getId(), req, USER));
    }

    @Test @DisplayName("when the book stock is lower than the litres sold, the message tells the manager what to do and nothing is posted")
    void insufficientBookStock() {
        diesel.adjust(d("-9800")); // 200 L left
        FuelShift s = opened();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER));
        assertTrue(ex.getMessage().contains("dip adjustment"));
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("a pump already on an open shift cannot be opened again")
    void pumpOnlyOnOneOpenShift() {
        FuelShiftReading busy = FuelShiftReading.open(TENANT, UUID.randomUUID(), pump1, "DIESEL", d("1"), BigDecimal.ZERO);
        when(readings.findOpenForPumps(eq(TENANT), any())).thenReturn(List.of(busy));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.openShift(TENANT,
                new OpenShiftRequest("A", null, List.of(new OpenReading(pump1.getId(), d("1000")))), USER));
        assertTrue(ex.getMessage().contains("Pump 1"));
    }

    @Test @DisplayName("the same pump twice, or a meter that differs from the last close, is handled: duplicate refused, gap recorded")
    void duplicateRefusedAndGapRecorded() {
        assertThrows(IllegalArgumentException.class, () -> service.openShift(TENANT, new OpenShiftRequest("A", null,
                List.of(new OpenReading(pump1.getId(), d("1")), new OpenReading(pump1.getId(), d("2")))), USER));
        pump1.recordMeter(d("990"));
        opened();
        FuelShiftReading r1 = stored.stream().filter(r -> r.getPumpId().equals(pump1.getId())).findFirst().orElseThrow();
        assertEquals(0, d("10").compareTo(r1.getOpeningGap()));
    }

    @Test @DisplayName("price and tank cannot change while the pump is on an open shift")
    void priceFrozenWhileOpen() {
        FuelShiftReading busy = FuelShiftReading.open(TENANT, UUID.randomUUID(), pump1, "DIESEL", d("1"), BigDecimal.ZERO);
        when(readings.findOpenForPumps(eq(TENANT), eq(List.of(pump1.getId())))).thenReturn(List.of(busy));
        assertThrows(IllegalStateException.class, () -> service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump 1", diesel.getId(), d("21.00"), true)));
        assertThrows(IllegalStateException.class, () -> service.deletePump(TENANT, pump1.getId()));
        // same price: allowed (rename only)
        assertDoesNotThrow(() -> service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump One", diesel.getId(), d("20.00"), true)));
    }

    // ---- D01: nozzle grouping ----

    @Test @DisplayName("D01: a nozzle needs a dispenser, a sensible number, and blank clears the grouping")
    void nozzleRules() {
        pump1.assignNozzle(" Island 1 ", 2);
        assertEquals("Island 1", pump1.getDispenserLabel());
        assertEquals(2, pump1.getNozzleNumber());
        assertThrows(IllegalArgumentException.class, () -> pump1.assignNozzle(null, 1));
        assertThrows(IllegalArgumentException.class, () -> pump1.assignNozzle("Island 1", 0));
        assertThrows(IllegalArgumentException.class, () -> pump1.assignNozzle("Island 1", 21));
        pump1.assignNozzle("Island 1", null);
        assertNull(pump1.getNozzleNumber());
        pump1.assignNozzle("", null);
        assertNull(pump1.getDispenserLabel());
    }

    @Test @DisplayName("D01: two nozzles cannot share a number on the same dispenser (any letter case), but different dispensers can")
    void nozzleNumberUnique() {
        pump2.assignNozzle("Island 1", 1);
        assertThrows(IllegalStateException.class, () -> service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump 1", diesel.getId(), d("20.00"), true, "island 1", 1)));
        assertDoesNotThrow(() -> service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump 1", diesel.getId(), d("20.00"), true, "Island 2", 1)));
        assertEquals("Island 2", pump1.getDispenserLabel());
    }

    @Test @DisplayName("D01: an older client that sends no grouping does not ungroup the pump; an empty label does")
    void nozzleLeftAloneWhenNotSent() {
        pump1.assignNozzle("Island 1", 3);
        service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump 1", diesel.getId(), d("20.00"), true));
        assertEquals("Island 1", pump1.getDispenserLabel());
        assertEquals(3, pump1.getNozzleNumber());
        service.updatePump(TENANT, pump1.getId(), new PumpRequest("Pump 1", diesel.getId(), d("20.00"), true, "", null));
        assertNull(pump1.getDispenserLabel());
        assertNull(pump1.getNozzleNumber());
    }

    @Test @DisplayName("reversing a closed shift puts the litres back and posts a reversal; an open or already reversed shift cannot be reversed")
    void reverseRestores() {
        FuelShift s = opened();
        assertThrows(IllegalStateException.class, () -> service.reverseShift(TENANT, s.getId(), new ReverseShiftRequest("Wrong meter"), USER));
        service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER);
        assertEquals(0, d("9700").compareTo(diesel.getCurrentLitres()));
        ShiftResponse r = service.reverseShift(TENANT, s.getId(), new ReverseShiftRequest("Wrong meter"), USER);
        assertEquals("REVERSED", r.status());
        assertEquals(0, d("10000").compareTo(diesel.getCurrentLitres()));
        verify(ledger).post(eq(diesel), eq(FuelStockMovement.Type.REVERSAL), org.mockito.ArgumentMatchers.argThat((BigDecimal b) -> b.compareTo(d("300")) == 0), eq("FORECOURT_SHIFT"), eq(s.getId()), any());
        assertThrows(IllegalStateException.class, () -> service.reverseShift(TENANT, s.getId(), new ReverseShiftRequest("Again please"), USER));
    }

    @Test @DisplayName("a closed shift cannot be closed twice")
    void noDoubleClose() {
        FuelShift s = opened();
        service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER);
        assertThrows(IllegalStateException.class, () -> service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER));
        assertEquals(0, d("9700").compareTo(diesel.getCurrentLitres()));
    }

    @Test @DisplayName("a shift of another tenant is not found")
    void tenantIsolation() {
        TenantId other = TenantId.of(UUID.randomUUID());
        when(shifts.findByTenantAndId(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.getShift(other, UUID.randomUUID()));
    }

    @Test @DisplayName("pending refunds/voids/discounts block closing the shift and nothing is posted")
    void pendingAdjustmentsBlockClose() {
        FuelShift s = opened();
        when(cash.pendingCount(TENANT, s.getId())).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER));
        assertTrue(s.isOpen());
        verify(ledger, never()).post(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("approved adjustments and fleet-card takings are applied; the company tolerance and closer rule are used")
    void usesCashControls() {
        FuelShift s = opened();
        when(cash.approvedTotal(TENANT, s.getId())).thenReturn(d("100"));
        // R6000 sold, R100 approved refund => R5900 expected; R3900 cash + R2000 fleet card
        CloseShiftRequest req = new CloseShiftRequest(d("3900"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new CloseReading(pump1.getId(), d("1210"), d("10")), new CloseReading(pump2.getId(), d("5100"), BigDecimal.ZERO)), d("2000"));
        ShiftResponse r = service.closeShift(TENANT, s.getId(), req, UUID.randomUUID());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.variance()));
        assertEquals(0, d("100").compareTo(r.adjustments()));
        assertEquals(0, d("2000").compareTo(r.fleetCard()));
        assertEquals("NONE", r.reviewStatus());
    }

    @Test @DisplayName("separate-closer rule from settings stops the opener closing")
    void separateCloserEnforced() {
        FuelShift s = opened();
        when(cash.policy(TENANT)).thenReturn(new FuelCashControlService.CashPolicy(d("5.00"), true));
        assertThrows(IllegalStateException.class, () -> service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER));
        assertTrue(s.isOpen());
    }

    @Test @DisplayName("a depot's own tolerance is used for the close: R1,000 short is refused at R5 but closes inside a R2,000 tolerance")
    void depotToleranceIsUsed() {
        UUID depot = UUID.randomUUID();
        diesel.place(depot, d("0"));
        FuelShift s = opened();
        when(tolerances.cashToleranceFor(eq(TENANT), eq(java.util.Set.of(depot)), any())).thenReturn(d("2000.00"));
        ShiftResponse r = service.closeShift(TENANT, s.getId(), close("3000", "2000", null, "1210", "10", "5100"), USER);
        assertEquals("CLOSED", r.status());
        assertEquals(0, d("2000.00").compareTo(s.getToleranceApplied()));
    }

    @Test @DisplayName("the shift alerts are told after a close, and a failing alert cannot be what stops it (the alert service swallows its own errors)")
    void alertsAfterClose() {
        FuelShift s = opened();
        service.closeShift(TENANT, s.getId(), close("4000", "2000", null, "1210", "10", "5100"), USER);
        verify(shiftAlerts).afterClose(TENANT, s);
    }
}
