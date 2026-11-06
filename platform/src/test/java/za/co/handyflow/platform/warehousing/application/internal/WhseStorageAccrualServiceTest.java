package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.model.WhseStorageAccrual;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseStorageAccrualRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhseStorageAccrualServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 11, 5);

    private WhseStorageAccrualRepository accruals;
    private WhseClientRepository clients;
    private WhseItemRepository items;
    private WhseProfileRepository profiles;
    private WhseStorageAccrualService service;
    private WhseClient client;
    private WhseItem item;

    @BeforeEach
    void setUp() {
        accruals = mock(WhseStorageAccrualRepository.class);
        clients = mock(WhseClientRepository.class);
        items = mock(WhseItemRepository.class);
        profiles = mock(WhseProfileRepository.class);
        service = new WhseStorageAccrualService(accruals, clients, items, profiles);
        client = WhseClient.create(tenant, "Acme", null, null, null, null, null, null, null, null, null);
        item = WhseItem.create(tenant, client.getId(), "SKU1", "Widget", "EACH", null);
        when(clients.findById(client.getId())).thenReturn(Optional.of(client));
        when(items.findAllActiveForClient(tenant, client.getId())).thenReturn(List.of(item));
        when(accruals.findAccruedInventoryIds(tenant, day)).thenReturn(List.of());
    }

    private WhseInventory stock(int qty) {
        WhseInventory inv = WhseInventory.create(tenant, client.getId(), item.getId(), UUID.randomUUID());
        inv.increaseOnHand(BigDecimal.valueOf(qty));
        return inv;
    }

    private void profileRate(String rate) {
        WhseProfile p = WhseProfile.create(tenant, "WH", null, rate == null ? null : new BigDecimal(rate),
                null, null, null, null, null, null);
        when(profiles.findByTenantId(tenant)).thenReturn(Optional.of(p));
    }

    @SuppressWarnings("unchecked")
    private List<WhseStorageAccrual> saved() {
        ArgumentCaptor<List<WhseStorageAccrual>> c = ArgumentCaptor.forClass(List.class);
        verify(accruals).saveAll(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("a day of storage is quantity x monthly rate / 30, with the rate source recorded")
    void dailyAmount() {
        profileRate("3.0000");
        when(accruals.findStockedPositions(tenant)).thenReturn(List.of(stock(100)));

        assertEquals(1, service.accrueForTenant(tenant, day));

        WhseStorageAccrual a = saved().get(0);
        assertEquals(0, new BigDecimal("10.0000").compareTo(a.getAmount()));
        assertEquals("PROFILE", a.getRateSource());
        assertEquals(day, a.getAccrualDate());
        assertEquals("AVAILABLE", a.getStatus());
    }

    @Test
    @DisplayName("item rate beats client rate beats warehouse default")
    void rateOrder() {
        WhseProfile p = WhseProfile.create(tenant, "WH", null, new BigDecimal("1"), null, null, null, null, null, null);
        assertEquals("PROFILE", WhseStorageAccrualService.resolve(item, client, p).source());
        WhseClient withRate = WhseClient.create(tenant, "B", null, new BigDecimal("2"), null, null, null, null, null, null, null);
        assertEquals("CLIENT", WhseStorageAccrualService.resolve(item, withRate, p).source());
        WhseItem rated = WhseItem.create(tenant, withRate.getId(), "S", "d", "EACH", new BigDecimal("3"));
        assertEquals("ITEM", WhseStorageAccrualService.resolve(rated, withRate, p).source());
        assertNull(WhseStorageAccrualService.resolve(item, client, null));
    }

    @Test
    @DisplayName("running twice for the same day adds nothing the second time")
    void idempotentForTheDay() {
        profileRate("3.0000");
        WhseInventory inv = stock(10);
        when(accruals.findStockedPositions(tenant)).thenReturn(List.of(inv));
        when(accruals.findAccruedInventoryIds(tenant, day)).thenReturn(List.of(inv.getId()));

        assertEquals(0, service.accrueForTenant(tenant, day));
        assertTrue(saved().isEmpty());
    }

    @Test
    @DisplayName("a position with no rate anywhere is skipped, not guessed")
    void noRateNoAccrual() {
        profileRate(null);
        when(accruals.findStockedPositions(tenant)).thenReturn(List.of(stock(10)));

        assertEquals(0, service.accrueForTenant(tenant, day));
    }

    @Test
    @DisplayName("an accrual cannot be billed twice")
    void billedOnce() {
        WhseStorageAccrual a = WhseStorageAccrual.of(stock(5), day, new BigDecimal("3"), "ITEM");
        a.markBilled(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> a.markBilled(UUID.randomUUID()));
    }
}
