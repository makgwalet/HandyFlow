package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelGaugeReading;
import za.co.handyflow.platform.fuel.domain.model.FuelImportAutoAccept;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.repository.FuelGaugeReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportAutoAcceptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelImportAutoAcceptServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    @Mock FuelImportAutoAcceptRepository settings;
    @Mock FuelPosImportRepository posImports;
    @Mock FuelGaugeReadingRepository gaugeReadings;
    @Mock FuelPosImportService pos;
    @Mock FuelGaugeImportService gauges;
    @Mock za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository shiftRepo;
    @Mock za.co.handyflow.platform.fuel.domain.repository.FuelShiftAutoAcceptLogRepository acceptLog;
    @Mock FuelShiftAlertService alerts;
    @Mock FuelToleranceService tolerances;
    @InjectMocks FuelImportAutoAcceptService service;

    @org.junit.jupiter.api.BeforeEach
    void defaults() { when(tolerances.gaugeLimitFor(any(), any(), any())).thenAnswer(i -> i.getArgument(2)); }

    final UUID id = UUID.randomUUID();
    final IngestResult pending = new IngestResult(id, "PENDING", false, List.of());

    private FuelImportAutoAccept setting(boolean shifts, boolean gauges, String max) {
        FuelImportAutoAccept a = FuelImportAutoAccept.forTenant(T);
        a.update(shifts, gauges, max == null ? null : new BigDecimal(max), null);
        when(settings.findForTenant(T)).thenReturn(Optional.of(a));
        return a;
    }

    private FuelPosImport pendingShift() {
        FuelPosImport imp = mock(FuelPosImport.class);
        when(imp.isPending()).thenReturn(true); when(imp.getId()).thenReturn(id); when(imp.getTenantId()).thenReturn(T);
        when(posImports.findById(id)).thenReturn(Optional.of(imp));
        return imp;
    }

    private FuelGaugeReading pendingGauge() {
        FuelGaugeReading g = mock(FuelGaugeReading.class);
        when(g.isPending()).thenReturn(true); when(g.getId()).thenReturn(id); when(g.getTenantId()).thenReturn(T);
        when(gaugeReadings.findById(id)).thenReturn(Optional.of(g));
        return g;
    }

    @Test @DisplayName("a company that has not switched it on is never auto-accepted")
    void offByDefault() {
        pendingShift(); pendingGauge();
        when(settings.findForTenant(T)).thenReturn(Optional.empty());
        assertEquals("PENDING", service.afterShift(pending).status());
        assertEquals("PENDING", service.afterGauge(pending).status());
        verify(pos, never()).accept(any(), any(), any(), any());
        verify(gauges, never()).accept(any(), any(), any(), any());
    }

    @Test @DisplayName("a clean shift is accepted automatically when switched on, with no deciding user")
    void acceptsCleanShift() {
        pendingShift(); setting(true, false, null);
        assertEquals("ACCEPTED", service.afterShift(pending).status());
        verify(pos).accept(eq(T), eq(id), any(), isNull());
    }

    @Test @DisplayName("a shift with problems, a repeat, or one the normal rules refuse is left for a person")
    void leavesProblemShifts() {
        pendingShift(); setting(true, false, null);
        assertEquals("PENDING", service.afterShift(new IngestResult(id, "PENDING", false, List.of("Pump \"P9\" is not set up"))).status());
        assertEquals("PENDING", service.afterShift(new IngestResult(id, "PENDING", true, List.of())).status());
        verify(pos, never()).accept(any(), any(), any(), any());
        when(pos.accept(any(), any(), any(), any())).thenThrow(new IllegalStateException("A reason is needed"));
        assertEquals("PENDING", service.afterShift(pending).status());
    }

    @Test @DisplayName("a gauge reading within the company's limit is accepted; beyond it, or an unknown tank, is left")
    void gaugeLimit() {
        pendingGauge(); setting(false, true, "500");
        when(gauges.differenceFromBook(T, id)).thenReturn(new BigDecimal("500"));
        assertEquals("ACCEPTED", service.afterGauge(pending).status());
        when(gauges.differenceFromBook(T, id)).thenReturn(new BigDecimal("500.01"));
        assertEquals("PENDING", service.afterGauge(pending).status());
        when(gauges.differenceFromBook(T, id)).thenReturn(null);
        assertEquals("PENDING", service.afterGauge(pending).status());
        verify(gauges, times(1)).accept(any(), any(), any(), any());
    }

    @Test @DisplayName("switching gauges on needs a limit; shifts alone need none")
    void gaugesNeedALimit() {
        FuelImportAutoAccept a = FuelImportAutoAccept.forTenant(T);
        assertThrows(IllegalArgumentException.class, () -> a.update(false, true, null, null));
        assertThrows(IllegalArgumentException.class, () -> a.update(false, true, BigDecimal.ZERO, null));
        a.update(true, false, null, null);
        assertTrue(a.isShiftsEnabled());
        assertFalse(a.isGaugesEnabled());
    }

    @Test @DisplayName("saving creates the company's row and returns what was saved")
    void saves() {
        when(settings.findForTenant(T)).thenReturn(Optional.empty());
        var s = service.save(T, true, true, new BigDecimal("300"), UUID.randomUUID());
        assertTrue(s.shiftsEnabled()); assertTrue(s.gaugesEnabled());
        assertEquals(new BigDecimal("300"), s.gaugeMaxDifferenceLitres());
        verify(settings).save(any());
    }

    @Test @DisplayName("an automatic acceptance is logged with the variance and tolerance, and the supervisor is told")
    void acceptanceIsLoggedAndNotified() {
        var imp = pendingShift(); setting(true, false, null);
        var shift = za.co.handyflow.platform.fuel.domain.model.FuelShift.open(T, "SH-7", "Thabo", null, null);
        shift.close(new BigDecimal("6000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0"), new BigDecimal("6000"), BigDecimal.ZERO, new BigDecimal("300"), null, null, new BigDecimal("5.00"), false);
        when(shiftRepo.findByTenantAndId(T, shift.getId())).thenReturn(Optional.of(shift));
        when(pos.accept(eq(T), eq(id), any(), isNull())).thenReturn(new za.co.handyflow.platform.fuel.dto.PosImportDtos.ImportView(id, "src", "EXT-1", "ACCEPTED", null, List.of(), null, null, null, null, shift.getId()));
        assertEquals("ACCEPTED", service.afterShift(pending).status());
        var cap = org.mockito.ArgumentCaptor.forClass(za.co.handyflow.platform.fuel.domain.model.FuelShiftAutoAcceptLog.class);
        verify(acceptLog).save(cap.capture());
        assertEquals("SYSTEM", cap.getValue().getActor());
        assertEquals(0, new BigDecimal("5.00").compareTo(cap.getValue().getToleranceApplied()));
        verify(alerts).autoAccepted(T, shift);
    }

    @Test @DisplayName("a failing log or notice does not undo the acceptance")
    void logFailureKeepsAcceptance() {
        pendingShift(); setting(true, false, null);
        var shift = za.co.handyflow.platform.fuel.domain.model.FuelShift.open(T, "SH-8", "Thabo", null, null);
        when(shiftRepo.findByTenantAndId(T, shift.getId())).thenReturn(Optional.of(shift));
        when(pos.accept(eq(T), eq(id), any(), isNull())).thenReturn(new za.co.handyflow.platform.fuel.dto.PosImportDtos.ImportView(id, "src", "EXT-1", "ACCEPTED", null, List.of(), null, null, null, null, shift.getId()));
        when(acceptLog.save(any())).thenThrow(new RuntimeException("db"));
        assertEquals("ACCEPTED", service.afterShift(pending).status());
    }

    @Test @DisplayName("a refusal leaves the shift for a person and tells the exception recipients the reason")
    void refusalNotifiesExceptions() {
        var imp = pendingShift(); when(imp.getExternalId()).thenReturn("EXT-9"); setting(true, false, null);
        when(pos.accept(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("The takings are R 1000.00 short"));
        assertEquals("PENDING", service.afterShift(pending).status());
        verify(alerts).notAccepted(T, "EXT-9", "The takings are R 1000.00 short");
        verify(acceptLog, never()).save(any());
    }

    @Test @DisplayName("a fuel type's own gauge limit is used instead of the company-wide one")
    void productGaugeLimitUsed() {
        pendingGauge(); setting(false, true, "500");
        when(gauges.differenceFromBook(T, id)).thenReturn(new BigDecimal("300"));
        when(gauges.fuelTypeOf(T, id)).thenReturn("PETROL");
        when(tolerances.gaugeLimitFor(eq(T), eq("PETROL"), argThat(b -> b != null && b.compareTo(new BigDecimal("500")) == 0))).thenReturn(new BigDecimal("200")); // by value: the stored 500 may carry any scale
        assertEquals("PENDING", service.afterGauge(pending).status());
    }
}
