package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelPump;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelPumpRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelOfflineServiceTest {
    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final TenantId OTHER = TenantId.of(UUID.randomUUID());
    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    static final UUID USER = UUID.randomUUID();

    @Mock FuelForecourtService forecourt;
    @Mock FuelTruckStopStayService stayService;
    @Mock FuelShiftRepository shifts;
    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelPumpRepository pumps;
    @Mock FuelOfflineConflictService conflicts;
    @Mock FuelSettingsService settings;
    @Mock AuditTrailFacade audit;
    @InjectMocks FuelOfflineService service;

    UUID rid = UUID.randomUUID(), pumpId = UUID.randomUUID(), shiftId = UUID.randomUUID(), stayId = UUID.randomUUID();
    ShiftResponse shiftOut = mock(ShiftResponse.class);
    StayView stayOut = mock(StayView.class);
    FuelShift shift = mock(FuelShift.class);
    FuelTruckStopStay stay = mock(FuelTruckStopStay.class);

    @BeforeEach void setUp() {
        when(settings.offlinePolicy(any())).thenReturn(new za.co.handyflow.platform.fuel.dto.PushDtos.OfflinePolicy(72, 5));
        when(shiftOut.id()).thenReturn(shiftId);
        when(stayOut.id()).thenReturn(stayId);
        when(shift.getId()).thenReturn(shiftId);
        when(stay.getId()).thenReturn(stayId);
        when(forecourt.getShift(any(), eq(shiftId))).thenReturn(shiftOut);
        when(stayService.get(any(), eq(stayId), any())).thenReturn(stayOut);
        when(shifts.findByTenantAndId(any(), eq(shiftId))).thenReturn(Optional.of(shift));
        when(stays.findOwned(any(), eq(stayId))).thenReturn(Optional.of(stay));
        when(shifts.findByOpenRequest(any(), any())).thenReturn(Optional.empty());
        when(shifts.findByCloseRequest(any(), any())).thenReturn(Optional.empty());
        when(stays.findByBookRequest(any(), any())).thenReturn(Optional.empty());
        when(stays.findByCheckInRequest(any(), any())).thenReturn(Optional.empty());
        when(stays.findByCheckOutRequest(any(), any())).thenReturn(Optional.empty());
    }

    OpenShiftRequest open(UUID id, Instant captured, String meter) {
        return new OpenShiftRequest("Thandi", null, List.of(new OpenReading(pumpId, new BigDecimal(meter))), id, captured);
    }
    CloseShiftRequest close(UUID id, Instant captured) {
        return new CloseShiftRequest(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(new CloseReading(pumpId, new BigDecimal("1100"), null)), null, id, captured);
    }

    // ── shift open ────────────────────────────────────────────────────────────

    @Test void anOpenWithNoIdIsAnOrdinaryOnlineOpen() {
        OpenShiftRequest req = new OpenShiftRequest("Thandi", null, List.of(new OpenReading(pumpId, BigDecimal.ONE)));
        service.openShift(T, req, USER, NOW);
        verify(forecourt).openShift(T, req, USER);
        verifyNoInteractions(conflicts);
        verify(shifts, never()).findByOpenRequest(any(), any());
    }

    @Test void aFirstOfflineOpenIsStampedAndAudited() {
        when(forecourt.openShift(eq(T), any(), eq(USER))).thenReturn(shiftOut);
        service.openShift(T, open(rid, NOW.minusSeconds(3600), "1000"), USER, NOW);
        verify(shift).stampOpen(rid, NOW.minusSeconds(3600));
        verify(shifts).saveAndFlush(shift);
        verify(audit).record(eq(T), eq("FUEL_SHIFT_OPENED_OFFLINE_SYNC"), eq("FuelShift"), eq(shiftId), any(), any());
    }

    @Test void aDuplicateOpenReturnsTheFirstShiftAndOpensNothing() {
        when(shifts.findByOpenRequest(T, rid)).thenReturn(Optional.of(shift));
        ShiftResponse out = service.openShift(T, open(rid, NOW, "1000"), USER, NOW);
        assertSame(shiftOut, out);
        verify(forecourt, never()).openShift(any(), any(), any());
        verifyNoInteractions(conflicts);
    }

    @Test void aMeterBelowThePumpsLastReadingIsRefusedAndKept() {
        FuelPump p = mock(FuelPump.class);
        when(p.getId()).thenReturn(pumpId); when(p.getName()).thenReturn("Pump 1"); when(p.getLastMeter()).thenReturn(new BigDecimal("1200"));
        when(pumps.findActiveByIds(eq(T), any())).thenReturn(List.of(p));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.openShift(T, open(rid, NOW, "1000"), USER, NOW));
        assertTrue(e.getMessage().contains("does not run backwards"));
        verify(forecourt, never()).openShift(any(), any(), any());
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.SHIFT_OPEN), eq(rid), any(), eq(USER), anyString(), any());
    }

    @Test void anOpenCapturedTooLongAgoIsRefusedAndKept() {
        assertThrows(IllegalStateException.class, () -> service.openShift(T, open(rid, NOW.minusSeconds(73 * 3600), "1000"), USER, NOW));
        verify(forecourt, never()).openShift(any(), any(), any());
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.SHIFT_OPEN), eq(rid), any(), eq(USER), anyString(), any());
    }

    @Test void anOpenWhileAPumpIsAlreadyOnAShiftIsKeptAsAConflict() {
        when(forecourt.openShift(eq(T), any(), eq(USER))).thenThrow(new IllegalStateException("Already on an open shift: Pump 1. Close that shift first."));
        assertThrows(IllegalStateException.class, () -> service.openShift(T, open(rid, NOW, "1000"), USER, NOW));
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.SHIFT_OPEN), eq(rid), any(), eq(USER), eq("Already on an open shift: Pump 1. Close that shift first."), any());
        verify(shift, never()).stampOpen(any(), any());
    }

    @Test void aCompanyThatShortenedTheLimitRefusesAWriteTheDefaultWouldAccept() {
        when(settings.offlinePolicy(T)).thenReturn(new za.co.handyflow.platform.fuel.dto.PushDtos.OfflinePolicy(12, 5));
        assertThrows(IllegalStateException.class, () -> service.openShift(T, open(rid, NOW.minusSeconds(13 * 3600), "1000"), USER, NOW));
        verify(forecourt, never()).openShift(any(), any(), any());
    }

    @Test void aCompanyThatWidenedTheLimitAcceptsALongerWait() {
        when(settings.offlinePolicy(T)).thenReturn(new za.co.handyflow.platform.fuel.dto.PushDtos.OfflinePolicy(168, 5));
        when(forecourt.openShift(eq(T), any(), eq(USER))).thenReturn(shiftOut);
        assertDoesNotThrow(() -> service.openShift(T, open(rid, NOW.minusSeconds(100 * 3600), "1000"), USER, NOW));
    }

    // ── shift close ───────────────────────────────────────────────────────────

    @Test void aFirstOfflineCloseIsStampedAndAudited() {
        service.closeShift(T, shiftId, close(rid, NOW), USER, NOW);
        verify(forecourt).closeShift(eq(T), eq(shiftId), any(), eq(USER));
        verify(shift).stampClose(rid, NOW);
        verify(audit).record(eq(T), eq("FUEL_SHIFT_CLOSED_OFFLINE_SYNC"), eq("FuelShift"), eq(shiftId), any(), any());
    }

    @Test void aDuplicateCloseReturnsTheClosedShiftAndBooksNothingTwice() {
        FuelShift done = mock(FuelShift.class); when(done.getId()).thenReturn(shiftId);
        when(shifts.findByCloseRequest(T, rid)).thenReturn(Optional.of(done));
        assertSame(shiftOut, service.closeShift(T, shiftId, close(rid, NOW), USER, NOW));
        verify(forecourt, never()).closeShift(any(), any(), any(), any());
    }

    @Test void theSameCloseIdOnAnotherShiftIsNotAReplay() {
        FuelShift done = mock(FuelShift.class); when(done.getId()).thenReturn(UUID.randomUUID());
        when(shifts.findByCloseRequest(T, rid)).thenReturn(Optional.of(done));
        assertThrows(IllegalStateException.class, () -> service.closeShift(T, shiftId, close(rid, NOW), USER, NOW));
        verify(forecourt, never()).closeShift(any(), any(), any(), any());
    }

    @Test void aCloseOfAShiftSomeoneElseAlreadyClosedIsAConflict() {
        when(forecourt.closeShift(eq(T), eq(shiftId), any(), eq(USER))).thenThrow(new IllegalStateException("This shift is already closed."));
        assertThrows(IllegalStateException.class, () -> service.closeShift(T, shiftId, close(rid, NOW), USER, NOW));
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.SHIFT_CLOSE), eq(rid), eq(shiftId), eq(USER), eq("This shift is already closed."), any());
    }

    @Test void aCloseForAnOfflineOpenFindsTheShiftByTheOpenId() {
        UUID openRid = UUID.randomUUID();
        when(shifts.findByOpenRequest(T, openRid)).thenReturn(Optional.of(shift));
        service.closeByOpenRequest(T, openRid, close(rid, NOW), USER, NOW);
        verify(forecourt).closeShift(eq(T), eq(shiftId), any(), eq(USER));
    }

    @Test void aCloseWhoseOpenNeverArrivedIsRefusedAndKept() {
        UUID openRid = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.closeByOpenRequest(T, openRid, close(rid, NOW), USER, NOW));
        verify(forecourt, never()).closeShift(any(), any(), any(), any());
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.SHIFT_CLOSE), eq(rid), any(), eq(USER), anyString(), any());
    }

    // ── stays ─────────────────────────────────────────────────────────────────

    @Test void aCheckInWithNoBodyIsAnOrdinaryCheckIn() {
        service.checkIn(T, stayId, null, USER, TODAY, NOW);
        verify(stayService).checkIn(T, stayId, TODAY);
        verifyNoInteractions(conflicts);
    }

    @Test void aFirstOfflineCheckInIsStamped() {
        when(stayService.checkIn(T, stayId, TODAY)).thenReturn(stayOut);
        service.checkIn(T, stayId, new CheckInRequest(rid, NOW.minusSeconds(600)), USER, TODAY, NOW);
        verify(stay).stampCheckIn(rid, NOW.minusSeconds(600));
        verify(audit).record(eq(T), eq("FUEL_STAY_CHECKED_IN_OFFLINE_SYNC"), any(), eq(stayId), any(), any());
    }

    @Test void aDuplicateCheckInChecksTheTruckInOnce() {
        when(stays.findByCheckInRequest(T, rid)).thenReturn(Optional.of(stay));
        assertSame(stayOut, service.checkIn(T, stayId, new CheckInRequest(rid, NOW), USER, TODAY, NOW));
        verify(stayService, never()).checkIn(any(), any(), any());
    }

    @Test void aCheckInAfterAnotherDeviceAlreadyDidItIsAConflict() {
        when(stayService.checkIn(T, stayId, TODAY)).thenThrow(new IllegalStateException("Cannot check in a stay that is CHECKED_IN."));
        assertThrows(IllegalStateException.class, () -> service.checkIn(T, stayId, new CheckInRequest(rid, NOW), USER, TODAY, NOW));
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.STAY_CHECK_IN), eq(rid), eq(stayId), eq(USER), anyString(), any());
    }

    @Test void aFirstOfflineCheckOutIsStampedAndPricedByTheServer() {
        CheckOutRequest req = new CheckOutRequest(TODAY, rid, NOW);
        when(stayService.checkOut(T, stayId, req, TODAY)).thenReturn(stayOut);
        service.checkOut(T, stayId, req, USER, TODAY, NOW);
        verify(stay).stampCheckOut(rid, NOW);
        verify(stayService).checkOut(T, stayId, req, TODAY);
    }

    @Test void aCheckOutDatedInTheFutureIsRefusedAndKept() {
        assertThrows(IllegalArgumentException.class, () -> service.checkOut(T, stayId, new CheckOutRequest(TODAY.plusDays(1), rid, NOW), USER, TODAY, NOW));
        verify(stayService, never()).checkOut(any(), any(), any(), any());
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.STAY_CHECK_OUT), eq(rid), eq(stayId), eq(USER), anyString(), any());
    }

    @Test void aLateSyncedCheckOutKeepsTheDateItWasCapturedWith() {
        CheckOutRequest req = new CheckOutRequest(TODAY.minusDays(1), rid, NOW.minusSeconds(86400));
        when(stayService.checkOut(T, stayId, req, TODAY)).thenReturn(stayOut);
        service.checkOut(T, stayId, req, USER, TODAY, NOW);
        verify(stayService).checkOut(T, stayId, req, TODAY); // the captured date is passed through untouched; the server prices from it
    }

    @Test void aDuplicateCheckOutDoesNotCheckOutTwiceOrInvoiceTwice() {
        when(stays.findByCheckOutRequest(T, rid)).thenReturn(Optional.of(stay));
        assertSame(stayOut, service.checkOut(T, stayId, new CheckOutRequest(TODAY, rid, NOW), USER, TODAY, NOW));
        verify(stayService, never()).checkOut(any(), any(), any(), any());
    }

    @Test void aCheckOutIdUsedForAnotherStayIsNotAReplay() {
        FuelTruckStopStay other = mock(FuelTruckStopStay.class); when(other.getId()).thenReturn(UUID.randomUUID());
        when(stays.findByCheckOutRequest(T, rid)).thenReturn(Optional.of(other));
        assertThrows(IllegalStateException.class, () -> service.checkOut(T, stayId, new CheckOutRequest(TODAY, rid, NOW), USER, TODAY, NOW));
    }

    @Test void anOfflineBookingForAFullYardIsRefusedAndKeptNotInvented() {
        BookRequest req = new BookRequest("ABC 1 GP", null, null, TODAY, TODAY.plusDays(1), null, null, null, rid, NOW);
        when(stayService.book(T, req, USER, TODAY)).thenThrow(new IllegalStateException("No bay is free on 2026-11-18."));
        assertThrows(IllegalStateException.class, () -> service.book(T, req, USER, TODAY, NOW));
        verify(conflicts).record(eq(T), eq(FuelOfflineConflictService.STAY_BOOK), eq(rid), any(), eq(USER), anyString(), any());
        verify(stay, never()).stampBook(any());
    }

    @Test void aDuplicateBookingReturnsTheFirstStay() {
        when(stays.findByBookRequest(T, rid)).thenReturn(Optional.of(stay));
        BookRequest req = new BookRequest("ABC 1 GP", null, null, TODAY, TODAY.plusDays(1), null, null, null, rid, NOW);
        assertSame(stayOut, service.book(T, req, USER, TODAY, NOW));
        verify(stayService, never()).book(any(), any(), any(), any());
    }

    // ── tenant isolation ──────────────────────────────────────────────────────

    @Test void aRequestIdIsOnlyEverLookedUpInTheCallersTenant() {
        when(shifts.findByOpenRequest(OTHER, rid)).thenReturn(Optional.of(shift)); // another company used the same id
        when(forecourt.openShift(eq(T), any(), eq(USER))).thenReturn(shiftOut);
        service.openShift(T, open(rid, NOW, "1000"), USER, NOW);
        verify(forecourt).openShift(eq(T), any(), eq(USER)); // not mistaken for a replay
        verify(shifts, never()).findByOpenRequest(eq(OTHER), any());
    }

    @Test void aStayLookupIsInTheCallersTenantOnly() {
        when(stays.findByCheckInRequest(OTHER, rid)).thenReturn(Optional.of(stay));
        when(stayService.checkIn(T, stayId, TODAY)).thenReturn(stayOut);
        service.checkIn(T, stayId, new CheckInRequest(rid, NOW), USER, TODAY, NOW);
        verify(stayService).checkIn(T, stayId, TODAY);
    }
}
