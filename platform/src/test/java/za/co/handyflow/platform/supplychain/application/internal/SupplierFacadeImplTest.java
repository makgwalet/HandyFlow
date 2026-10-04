package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.SupplierFacade.SupplierSummary;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierFacadeImplTest {

    @Mock ScSupplierRepository repository;
    static final UUID TENANT_ID = UUID.randomUUID();
    static final TenantId TENANT = TenantId.of(TENANT_ID);

    private SupplierFacadeImpl facade() { return new SupplierFacadeImpl(repository); }

    private ScSupplier supplier(UUID id, String name, SupplierStatus status, UUID tenant, Instant deletedAt) {
        ScSupplier s = mock(ScSupplier.class);
        when(s.getId()).thenReturn(id); when(s.getName()).thenReturn(name); when(s.getStatus()).thenReturn(status); when(s.getTenantId()).thenReturn(tenant); when(s.getDeletedAt()).thenReturn(deletedAt);
        return s;
    }

    @Test
    @DisplayName("active suppliers are listed with identity only, asking Supply Chain for ACTIVE ones")
    void listsActive() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenantIdAndStatus(eq(TENANT_ID), eq(SupplierStatus.ACTIVE), any())).thenReturn(new PageImpl<>(List.of(supplier(id, "AgriSupplies", SupplierStatus.ACTIVE, TENANT_ID, null))));

        List<SupplierSummary> out = facade().listActive(TENANT, 100);

        assertEquals(1, out.size());
        assertEquals(new SupplierSummary(id, "AgriSupplies", "ACTIVE"), out.get(0));
        assertTrue(out.get(0).isActive());
        verify(repository).findByTenantIdAndStatus(eq(TENANT_ID), eq(SupplierStatus.ACTIVE), eq(PageRequest.of(0, 100)));
    }

    @Test
    @DisplayName("the list size is kept between 1 and 500")
    void listIsCapped() {
        when(repository.findByTenantIdAndStatus(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        facade().listActive(TENANT, 100000);
        verify(repository).findByTenantIdAndStatus(eq(TENANT_ID), eq(SupplierStatus.ACTIVE), eq(PageRequest.of(0, 500)));

        facade().listActive(TENANT, 0);
        verify(repository).findByTenantIdAndStatus(eq(TENANT_ID), eq(SupplierStatus.ACTIVE), eq(PageRequest.of(0, 1)));
    }

    @Test
    @DisplayName("one supplier is found whatever its status, and a blacklisted one says so and is not active")
    void findsAnyStatus() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenantIdAndId(eq(TENANT_ID), eq(id))).thenReturn(Optional.of(supplier(id, "Dodgy Ltd", SupplierStatus.BLACKLISTED, TENANT_ID, null)));

        SupplierSummary s = facade().find(TENANT, id).orElseThrow();

        assertEquals("BLACKLISTED", s.status());
        assertFalse(s.isActive());
        assertEquals("Dodgy Ltd", s.name());
    }

    @Test
    @DisplayName("an unknown or deleted supplier is empty")
    void findsNothing() {
        assertTrue(facade().find(TENANT, UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("a supplier with no status is not active, never assumed to be")
    void nullStatus() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenantIdAndId(eq(TENANT_ID), eq(id))).thenReturn(Optional.of(supplier(id, "Odd", null, TENANT_ID, null)));

        assertFalse(facade().find(TENANT, id).orElseThrow().isActive());
    }

    @Test
    @DisplayName("looking up several suppliers is one call, returned by id")
    void findsSeveral() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(repository.findAllById(any())).thenReturn(List.of(supplier(a, "A", SupplierStatus.ACTIVE, TENANT_ID, null), supplier(b, "B", SupplierStatus.INACTIVE, TENANT_ID, null)));

        Map<UUID, SupplierSummary> out = facade().findAll(TENANT, Set.of(a, b));

        assertEquals(Set.of(a, b), out.keySet());
        assertEquals("B", out.get(b).name());
        verify(repository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("another tenant's supplier and a deleted one are never returned, even though the repository lookup by id is not tenant-scoped")
    void findAllIsTenantSafe() {
        UUID mine = UUID.randomUUID(), theirs = UUID.randomUUID(), deleted = UUID.randomUUID();
        when(repository.findAllById(any())).thenReturn(List.of(supplier(mine, "Mine", SupplierStatus.ACTIVE, TENANT_ID, null),
                supplier(theirs, "Theirs", SupplierStatus.ACTIVE, UUID.randomUUID(), null), supplier(deleted, "Gone", SupplierStatus.ACTIVE, TENANT_ID, Instant.now())));

        Map<UUID, SupplierSummary> out = facade().findAll(TENANT, Set.of(mine, theirs, deleted));

        assertEquals(Set.of(mine), out.keySet());
    }

    @Test
    @DisplayName("no ids means no lookup at all")
    void noIds() {
        assertTrue(facade().findAll(TENANT, Set.of()).isEmpty());
        assertTrue(facade().findAll(TENANT, null).isEmpty());
        verify(repository, never()).findAllById(any());
    }
}
