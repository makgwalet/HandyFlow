package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryException;
import za.co.handyflow.platform.fuel.domain.model.FuelLoadReturn;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.Outstanding;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnRequest;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 7a: confirming returned fuel into a tank books no stock movement, and cannot confirm more than came back. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelLoadReturnServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelDeliveryExceptionRepository exceptions;
    @Mock FuelLoadReturnRepository returns;
    @Mock FuelTankRepository tanks;
    @Mock CrmFacade crm;
    @InjectMocks FuelLoadReturnService service;

    FuelTank tank;
    final List<FuelLoadReturn> saved = new ArrayList<>();
    final List<FuelDeliveryException> declared = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tank = mock(FuelTank.class); when(tank.getId()).thenReturn(UUID.randomUUID()); when(tank.getName()).thenReturn("Depot tank 1");
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.of(tank));
        when(tanks.findAllActive(T)).thenReturn(List.of(tank));
        when(returns.save(any())).thenAnswer(i -> { FuelLoadReturn r = i.getArgument(0); saved.add(r); return r; });
        when(returns.findByDelivery(eq(T), any())).thenAnswer(i -> saved.stream().filter(r -> r.getDeliveryId().equals(i.getArgument(1))).toList());
        when(returns.findByDeliveries(eq(T), any())).thenAnswer(i -> saved);
        when(exceptions.findByDelivery(eq(T), any())).thenAnswer(i -> declared.stream().filter(e -> e.getDeliveryId().equals(i.getArgument(1))).toList());
        when(exceptions.findAllWithReturnedLitres(T)).thenAnswer(i -> declared.stream().filter(e -> e.getLitresReturned().signum() > 0).toList());
    }

    private FuelDelivery load(String loaded) {
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), null, Map.of(), "DIESEL", bd("1000"), bd("22"), Instant.now(), null, null);
        d.dispatch("Driver", "GP 1");
        if (loaded != null) d.recordLoading(bd(loaded), null);
        when(deliveries.findActiveByIdForUpdate(eq(T), eq(d.getId()))).thenReturn(Optional.of(d));
        when(deliveries.findActiveById(eq(T), eq(d.getId()))).thenReturn(Optional.of(d));
        return d;
    }

    private void failedWithReturn(FuelDelivery d, String litres) {
        declared.add(FuelDeliveryException.create(T, d.getId(), "SITE_CLOSED", null, bd(litres), null, 1, BY, null));
    }

    private static ReturnRequest req(UUID tankId, String litres) { return new ReturnRequest(tankId, bd(litres), null, null); }

    @Test @DisplayName("fuel declared returned on a failed attempt can be confirmed into a tank, and it books no stock movement")
    void confirmsIntoTank() {
        FuelDelivery d = load("1000"); failedWithReturn(d, "1000");
        ReturnView v = service.record(T, d.getId(), req(tank.getId(), "1000"), BY);
        assertEquals(0, bd("1000").compareTo(v.litres())); assertEquals("Depot tank 1", v.tankName());
        verify(tank, never()).addStock(any(), any()); verify(tank, never()).removeStock(any());
        verifyNoInteractions(crm);
    }

    @Test @DisplayName("part can be confirmed now and the rest later, and never more than was declared in total")
    void cannotConfirmMoreThanDeclared() {
        FuelDelivery d = load("1000"); failedWithReturn(d, "600");
        service.record(T, d.getId(), req(tank.getId(), "400"), BY);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.record(T, d.getId(), req(tank.getId(), "300"), BY));
        assertTrue(e.getMessage().contains("200"));
        service.record(T, d.getId(), req(tank.getId(), "200"), BY);
        assertEquals(2, saved.size());
    }

    @Test @DisplayName("a load with nothing declared returned and nothing left over cannot have fuel confirmed back")
    void nothingToConfirm() {
        FuelDelivery d = load("1000");
        assertThrows(IllegalArgumentException.class, () -> service.record(T, d.getId(), req(tank.getId(), "10"), BY));
        assertTrue(saved.isEmpty());
    }

    @Test @DisplayName("after a delivery the loaded litres that were not delivered can be confirmed back")
    void leftoverAfterDelivery() {
        FuelDelivery d = load("1000"); d.complete(bd("940"), "R", null, null, null, false, null, null);
        service.record(T, d.getId(), req(tank.getId(), "60"), BY);
        assertThrows(IllegalArgumentException.class, () -> service.record(T, d.getId(), req(tank.getId(), "1"), BY));
    }

    @Test @DisplayName("an unknown tank or load is refused; a zero or negative quantity is refused")
    void refusals() {
        FuelDelivery d = load("1000"); failedWithReturn(d, "100");
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.record(T, d.getId(), req(UUID.randomUUID(), "10"), BY));
        assertThrows(ResourceNotFoundException.class, () -> service.record(T, UUID.randomUUID(), req(tank.getId(), "10"), BY));
        assertThrows(IllegalArgumentException.class, () -> FuelLoadReturn.create(T, d.getId(), tank.getId(), BigDecimal.ZERO, null, BY, null));
    }

    @Test @DisplayName("a retry with the same request id returns the first record and adds nothing")
    void retryIsIdempotent() {
        FuelDelivery d = load("1000"); failedWithReturn(d, "500");
        UUID rid = UUID.randomUUID();
        ReturnView first = service.record(T, d.getId(), new ReturnRequest(tank.getId(), bd("500"), null, rid), BY);
        when(returns.findByClientRequest(T, d.getId(), rid)).thenReturn(Optional.of(saved.get(0)));
        ReturnView again = service.record(T, d.getId(), new ReturnRequest(tank.getId(), bd("500"), null, rid), BY);
        assertEquals(first.id(), again.id()); assertEquals(1, saved.size());
    }

    @Test @DisplayName("outstanding lists loads declared returned and not fully confirmed, with what is left")
    void outstandingList() {
        FuelDelivery a = load("1000"); failedWithReturn(a, "600");
        FuelDelivery b = load("500"); failedWithReturn(b, "200");
        service.record(T, a.getId(), req(tank.getId(), "250"), BY);
        service.record(T, b.getId(), req(tank.getId(), "200"), BY);   // b is fully confirmed
        List<Outstanding> out = service.outstanding(T);
        assertEquals(1, out.size()); assertEquals(a.getId(), out.get(0).deliveryId());
        assertEquals(0, bd("600").compareTo(out.get(0).declaredReturned())); assertEquals(0, bd("250").compareTo(out.get(0).confirmedBack())); assertEquals(0, bd("350").compareTo(out.get(0).outstanding()));
    }

    @Test @DisplayName("nothing declared returned gives an empty outstanding list")
    void outstandingEmpty() { assertTrue(service.outstanding(T).isEmpty()); }
}
