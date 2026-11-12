package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryException;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryExceptionRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.ExceptionView;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.FailedAttemptRequest;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.WindowRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuelDeliveryExceptionServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelDeliveryExceptionRepository exceptions;
    @Mock HrFacade hr;
    FuelDeliveryExceptionService service;

    @BeforeEach void setUp() { service = new FuelDeliveryExceptionService(deliveries, exceptions, hr); }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelDelivery onTheRoad(String loaded) {
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), null, "DIESEL", bd("1000"), bd("20"), Instant.now(), null, null);
        d.dispatch("Driver", "GP 1");
        if (loaded != null) d.recordLoading(bd(loaded), null);
        when(deliveries.findActiveByIdForUpdate(any(TenantId.class), eq(d.getId()))).thenReturn(Optional.of(d));
        lenient().when(exceptions.save(any())).thenAnswer(i -> i.getArgument(0));
        return d;
    }

    private static FailedAttemptRequest req(String reason, String note, String litres, Instant again, UUID rid) {
        return new FailedAttemptRequest(reason, note, litres == null ? null : bd(litres), again, rid);
    }

    @Test @DisplayName("a failed attempt puts the load back to Scheduled, counts the attempt and keeps a record")
    void failedAttemptReschedules() {
        FuelDelivery d = onTheRoad("1000");
        Instant tomorrow = Instant.now().plusSeconds(86400);
        ExceptionView v = service.recordAsOffice(T, d.getId(), req("SITE_CLOSED", null, "1000", tomorrow, null), BY);
        assertEquals("SCHEDULED", d.getStatus());
        assertEquals(1, d.getFailedAttempts());
        assertEquals(tomorrow, d.getScheduledAt());
        assertEquals(1, v.attemptNumber()); assertEquals("SITE_CLOSED", v.reasonCode()); assertEquals(0, bd("1000").compareTo(v.litresReturned()));
        verify(deliveries).save(d);
    }

    @Test @DisplayName("a second failure is attempt 2 and the load can be dispatched again in between")
    void secondFailure() {
        FuelDelivery d = onTheRoad("500");
        service.recordAsOffice(T, d.getId(), req("NO_ACCESS", null, null, null, null), BY);
        d.dispatch("Driver", "GP 1");
        ExceptionView v = service.recordAsOffice(T, d.getId(), req("REFUSED", null, null, null, null), BY);
        assertEquals(2, v.attemptNumber()); assertEquals(2, d.getFailedAttempts());
    }

    @Test @DisplayName("only a load on the road can fail; a scheduled or delivered one is refused and nothing is saved")
    void onlyInTransit() {
        FuelDelivery d = onTheRoad(null);
        service.recordAsOffice(T, d.getId(), req("SITE_CLOSED", null, null, null, null), BY); // back to SCHEDULED
        assertThrows(IllegalStateException.class, () -> service.recordAsOffice(T, d.getId(), req("SITE_CLOSED", null, null, null, null), BY));
        verify(exceptions, times(1)).save(any());
    }

    @Test @DisplayName("the reason must be one of the list, and Other needs a note; the load stays on the road when refused")
    void reasonValidation() {
        FuelDelivery d = onTheRoad(null);
        assertThrows(IllegalArgumentException.class, () -> service.recordAsOffice(T, d.getId(), req("BECAUSE", null, null, null, null), BY));
        assertThrows(IllegalArgumentException.class, () -> service.recordAsOffice(T, d.getId(), req("OTHER", "  ", null, null, null), BY));
        assertEquals("IN_TRANSIT", d.getStatus()); assertEquals(0, d.getFailedAttempts());
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("litres returned cannot exceed what was loaded, and cannot be returned when nothing was loaded")
    void returnedLitres() {
        FuelDelivery d = onTheRoad("800");
        assertThrows(IllegalArgumentException.class, () -> service.recordAsOffice(T, d.getId(), req("REFUSED", null, "800.01", null, null), BY));
        assertThrows(IllegalArgumentException.class, () -> service.recordAsOffice(T, d.getId(), req("REFUSED", null, "-1", null, null), BY));
        assertEquals("IN_TRANSIT", d.getStatus());
        FuelDelivery noLoad = onTheRoad(null);
        assertThrows(IllegalArgumentException.class, () -> service.recordAsOffice(T, noLoad.getId(), req("REFUSED", null, "5", null, null), BY));
    }

    @Test @DisplayName("a replayed request id returns the first record and changes nothing")
    void replayIsIdempotent() {
        FuelDelivery d = onTheRoad("100");
        UUID rid = UUID.randomUUID();
        FuelDeliveryException first = FuelDeliveryException.create(T, d.getId(), "SITE_CLOSED", null, bd("0"), null, 1, BY, rid);
        when(exceptions.findByClientRequest(T, d.getId(), rid)).thenReturn(Optional.of(first));
        ExceptionView v = service.recordAsOffice(T, d.getId(), req("SITE_CLOSED", null, null, null, rid), BY);
        assertEquals(first.getId(), v.id());
        assertEquals("IN_TRANSIT", d.getStatus()); assertEquals(0, d.getFailedAttempts());
        verify(exceptions, never()).save(any());
    }

    @Test @DisplayName("a driver can only fail their own load; another driver's load looks missing")
    void driverOwnLoadOnly() {
        FuelDelivery d = onTheRoad(null);
        UUID user = UUID.randomUUID();
        when(hr.findEmployeeByUserId(T, user)).thenReturn(Optional.of(new za.co.handyflow.platform.hr.EmployeeSummary(UUID.randomUUID(), "E2", "Other", "Driver", "Other Driver", null, null, "Driver", null, "ACTIVE")));
        assertThrows(ResourceNotFoundException.class, () -> service.recordAsDriver(T, user, d.getId(), req("SITE_CLOSED", null, null, null, null)));
        assertThrows(IllegalStateException.class, () -> service.recordAsDriver(T, null, d.getId(), req("SITE_CLOSED", null, null, null, null)));
        assertEquals("IN_TRANSIT", d.getStatus());
    }

    @Test @DisplayName("a window must end after it starts; either end may be open; outside-window is computed from it")
    void windows() {
        FuelDelivery d = onTheRoad(null);
        Instant a = Instant.parse("2026-11-13T08:00:00Z"), b = Instant.parse("2026-11-13T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.setWindow(T, d.getId(), new WindowRequest(b, a)));
        service.setWindow(T, d.getId(), new WindowRequest(a, b));
        assertFalse(d.isOutsideWindow(Instant.parse("2026-11-13T10:00:00Z")));
        assertTrue(d.isOutsideWindow(Instant.parse("2026-11-13T13:00:00Z")));
        assertTrue(d.isOutsideWindow(Instant.parse("2026-11-13T07:00:00Z")));
        service.setWindow(T, d.getId(), new WindowRequest(null, b));
        assertFalse(d.isOutsideWindow(Instant.parse("2026-11-13T07:00:00Z")));
        service.setWindow(T, d.getId(), new WindowRequest(null, null));
        assertFalse(d.isOutsideWindow(Instant.now()));
    }

    @Test @DisplayName("a missing load is not found")
    void missing() {
        when(deliveries.findActiveByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.recordAsOffice(T, UUID.randomUUID(), req("SITE_CLOSED", null, null, null, null), BY));
    }
}
