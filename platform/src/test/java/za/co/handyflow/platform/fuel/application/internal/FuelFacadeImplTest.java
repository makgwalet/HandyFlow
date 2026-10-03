package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.fuel.application.FuelFacade.OwnDispatch;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelDispatchRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelFacadeImplTest {

    @Mock FuelDispatchRepository dispatchRepository;
    @Mock FuelTankRepository tankRepository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z"), TO = Instant.parse("2026-10-01T00:00:00Z");
    final UUID tankId = UUID.randomUUID(), vehicleId = UUID.randomUUID();

    private FuelFacadeImpl facade() { return new FuelFacadeImpl(dispatchRepository, tankRepository); }

    private FuelDispatch dispatch(UUID id, UUID tank, UUID vehicle, UUID asset, String litres, String cost) {
        FuelDispatch d = mock(FuelDispatch.class);
        when(d.getId()).thenReturn(id);
        when(d.getTankId()).thenReturn(tank);
        when(d.getVehicleId()).thenReturn(vehicle);
        when(d.getAssetId()).thenReturn(asset);
        when(d.getRecipientName()).thenReturn("Tractor 1");
        when(d.getLitresDispensed()).thenReturn(new BigDecimal(litres));
        when(d.getCostPerLitreAtSale()).thenReturn(cost == null ? null : new BigDecimal(cost));
        when(d.getDispatchedAt()).thenReturn(Instant.parse("2026-09-15T08:30:00Z"));
        when(d.getHoursReading()).thenReturn(new BigDecimal("1234.5"));
        return d;
    }

    private void tankNamed(UUID id, String name) {
        FuelTank t = mock(FuelTank.class);
        when(t.getId()).thenReturn(id);
        when(t.getName()).thenReturn(name);
        when(tankRepository.findAllById(any())).thenReturn(List.of(t));
    }

    @Test
    @DisplayName("own dispatches carry the litres, the snapshotted cost per litre and the tank's name")
    void mapsDispatches() {
        UUID id = UUID.randomUUID();
        FuelDispatch d = dispatch(id, tankId, vehicleId, null, "500", "22.4");
        tankNamed(tankId, "Main diesel tank");
        when(dispatchRepository.findOwnBetween(eq(TENANT), eq(FROM), eq(TO), any())).thenReturn(List.of(d));

        OwnDispatch o = facade().findOwnDispatches(TENANT, FROM, TO, 100).get(0);

        assertEquals(id, o.id());
        assertEquals("Main diesel tank", o.tankName());
        assertEquals(vehicleId, o.vehicleId());
        assertEquals(0, new BigDecimal("500").compareTo(o.litres()));
        assertEquals(0, new BigDecimal("22.4").compareTo(o.costPerLitre()));
        assertEquals(0, new BigDecimal("1234.5").compareTo(o.hoursReading()));
    }

    @Test
    @DisplayName("tank names are looked up once for the whole list, and not at all for an empty one")
    void batchesTankLookups() {
        FuelDispatch one = dispatch(UUID.randomUUID(), tankId, vehicleId, null, "100", "22");
        FuelDispatch two = dispatch(UUID.randomUUID(), tankId, vehicleId, null, "200", "22");
        tankNamed(tankId, "Main diesel tank");
        when(dispatchRepository.findOwnBetween(eq(TENANT), eq(FROM), eq(TO), any())).thenReturn(List.of(one, two));

        facade().findOwnDispatches(TENANT, FROM, TO, 100);
        verify(tankRepository, times(1)).findAllById(any());

        clearInvocations(tankRepository);
        when(dispatchRepository.findOwnBetween(eq(TENANT), eq(FROM), eq(TO), any())).thenReturn(List.of());
        assertTrue(facade().findOwnDispatches(TENANT, FROM, TO, 100).isEmpty());
        verifyNoInteractions(tankRepository);
    }

    @Test
    @DisplayName("a dispatch with no cost recorded keeps a null cost: it is never guessed")
    void noCostStaysNull() {
        FuelDispatch d = dispatch(UUID.randomUUID(), tankId, vehicleId, null, "100", null);
        tankNamed(tankId, "Main diesel tank");
        when(dispatchRepository.findOwnBetween(eq(TENANT), eq(FROM), eq(TO), any())).thenReturn(List.of(d));

        assertNull(facade().findOwnDispatches(TENANT, FROM, TO, 100).get(0).costPerLitre());
    }

    @Test
    @DisplayName("the row cap is kept between 1 and 1000")
    void capsRows() {
        when(dispatchRepository.findOwnBetween(eq(TENANT), eq(FROM), eq(TO), any())).thenReturn(List.of());

        facade().findOwnDispatches(TENANT, FROM, TO, 5000);
        verify(dispatchRepository).findOwnBetween(eq(TENANT), eq(FROM), eq(TO), eq(PageRequest.of(0, 1000)));

        facade().findOwnDispatches(TENANT, FROM, TO, 0);
        verify(dispatchRepository).findOwnBetween(eq(TENANT), eq(FROM), eq(TO), eq(PageRequest.of(0, 1)));
    }

    @Test
    @DisplayName("one dispatch is found by id, and one that is not the tenant's own fuel is empty")
    void findsOne() {
        UUID id = UUID.randomUUID();
        FuelDispatch d = dispatch(id, tankId, vehicleId, null, "500", "22.4");
        tankNamed(tankId, "Main diesel tank");
        when(dispatchRepository.findOwnById(eq(TENANT), eq(id))).thenReturn(Optional.of(d));

        assertEquals(id, facade().findOwnDispatch(TENANT, id).orElseThrow().id());
        assertTrue(facade().findOwnDispatch(TENANT, UUID.randomUUID()).isEmpty());
    }
}
