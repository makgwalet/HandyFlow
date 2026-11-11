package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelLegalEntityRepository;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.LegalEntityRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelLegalEntityServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final TenantId OTHER = TenantId.of(UUID.randomUUID());

    @Mock FuelLegalEntityRepository entities;
    @Mock FuelDepotRepository depots;
    @InjectMocks FuelLegalEntityService service;

    private LegalEntityRequest req(String name) { return new LegalEntityRequest(name, "2015/123456/07", "9012345678", "4012345678", "1 Main Rd, Sandton"); }

    @Test @DisplayName("creating an entity trims, keeps SA registration and VAT numbers, and refuses a duplicate name")
    void create() {
        when(entities.nameTaken(T, "Acme Fuel (Pty) Ltd", null)).thenReturn(false);
        when(entities.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = service.create(T, req("  Acme Fuel (Pty) Ltd "), UUID.randomUUID());
        assertEquals("Acme Fuel (Pty) Ltd", r.name());
        assertEquals("4012345678", r.vatNo());
        assertEquals("ACTIVE", r.status());
        when(entities.nameTaken(T, "Acme Fuel (Pty) Ltd", null)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(T, req("Acme Fuel (Pty) Ltd"), UUID.randomUUID()));
    }

    @Test @DisplayName("an entity of another tenant cannot be read, changed, removed or attached to a depot")
    void tenantIsolation() {
        UUID id = UUID.randomUUID();
        when(entities.findActiveById(T, id)).thenReturn(Optional.empty());   // exists only under OTHER
        assertThrows(ResourceNotFoundException.class, () -> service.update(T, id, req("X")));
        assertThrows(ResourceNotFoundException.class, () -> service.remove(T, id));
        UUID depotId = UUID.randomUUID();
        when(depots.findActiveById(T, depotId)).thenReturn(Optional.of(FuelDepot.create(T, "Main", "MAIN", null)));
        assertThrows(ResourceNotFoundException.class, () -> service.assignDepot(T, depotId, id));
        verify(entities, never()).findActiveById(eq(OTHER), any());
    }

    @Test @DisplayName("an entity that owns depots cannot be removed")
    void removeBlocked() {
        FuelLegalEntity e = FuelLegalEntity.create(T, "Acme", null, null, null, null, null);
        when(entities.findActiveById(T, e.getId())).thenReturn(Optional.of(e));
        when(depots.countByLegalEntity(T, e.getId())).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> service.remove(T, e.getId()));
        when(depots.countByLegalEntity(T, e.getId())).thenReturn(0L);
        service.remove(T, e.getId());
        assertNotNull(e.getDeletedAt());
    }

    @Test @DisplayName("a depot can be assigned to an active entity, and cleared")
    void assign() {
        FuelLegalEntity e = FuelLegalEntity.create(T, "Acme", null, null, null, null, null);
        FuelDepot d = FuelDepot.create(T, "Main", "MAIN", null);
        when(entities.findActiveById(T, e.getId())).thenReturn(Optional.of(e));
        when(depots.findActiveById(T, d.getId())).thenReturn(Optional.of(d));
        service.assignDepot(T, d.getId(), e.getId());
        assertEquals(e.getId(), d.getLegalEntityId());
        service.assignDepot(T, d.getId(), null);
        assertNull(d.getLegalEntityId());
        e.setActive(false);
        assertThrows(IllegalStateException.class, () -> service.assignDepot(T, d.getId(), e.getId()));
    }

    @Test @DisplayName("a legal entity needs a name")
    void needsName() { assertThrows(IllegalArgumentException.class, () -> FuelLegalEntity.create(T, " ", null, null, null, null, null)); }
}
