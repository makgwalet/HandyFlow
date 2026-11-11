package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** A contract's billable-day rule decides whether a day accrues at all; with no rule every day does. */
class WhseBillableDayAccrualTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13), SATURDAY = FRIDAY.plusDays(1);
    private final UUID tenant = UUID.randomUUID();
    private WhseStorageAccrualRepository accruals;
    private WhseStorageAccrualService service;
    private WhseBillingDayService billingDays;
    private WhseClient client;
    private WhseInventory inv;

    @BeforeEach
    void setUp() {
        accruals = mock(WhseStorageAccrualRepository.class);
        WhseClientRepository clients = mock(WhseClientRepository.class);
        WhseItemRepository items = mock(WhseItemRepository.class);
        WhseProfileRepository profiles = mock(WhseProfileRepository.class);
        WhseInventoryRepository inventoryRepo = mock(WhseInventoryRepository.class);
        service = new WhseStorageAccrualService(accruals, clients, items, profiles, inventoryRepo, tenantId -> new WhseTariffBook(List.of(), List.of()),
                mock(za.co.handyflow.platform.warehousing.domain.repository.WhseLocationAccrualRepository.class));
        billingDays = mock(WhseBillingDayService.class);
        ReflectionTestUtils.setField(service, "billingDays", billingDays);

        client = WhseClient.create(tenant, "Acme", null, null, null, null, null, null, null, null, null);
        WhseItem item = WhseItem.create(tenant, client.getId(), "SKU1", "Widget", "EACH", null);
        inv = WhseInventory.create(tenant, client.getId(), item.getId(), UUID.randomUUID());
        when(clients.findById(client.getId())).thenReturn(Optional.of(client));
        when(items.findAllActiveForClient(tenant, client.getId())).thenReturn(List.of(item));
        when(accruals.findAccruedInventoryIds(eq(tenant), any(LocalDate.class))).thenReturn(List.of());
        when(profiles.findByTenantId(tenant)).thenReturn(Optional.of(WhseProfile.create(tenant, "WH", null, new BigDecimal("3.0000"), null, null, null, null, null, null)));
        when(accruals.billableQuantities(eq(tenant), any(java.time.Instant.class), any(java.time.Instant.class)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{inv.getId(), BigDecimal.valueOf(100)}));
        when(inventoryRepo.findAllById(any())).thenAnswer(i -> List.of(inv));
    }

    private void weekdaysOnly() {
        when(billingDays.book(any())).thenReturn(book(WhseBillableDayRules.DayRule.WEEKDAYS_ONLY));
    }

    private WhseBillingDayService.Book book(WhseBillableDayRules.DayRule rule) {
        var v = new WhseBillableDayRules.RuleVersion(client.getId(), rule, FRIDAY.minusDays(30), java.time.Instant.EPOCH);
        return WhseBillingDayService.Book.of(List.of(v), java.util.Set.of());
    }

    @Test @DisplayName("with no billable-day service the standard applies: a Saturday accrues")
    void standardSaturday() {
        ReflectionTestUtils.setField(service, "billingDays", null);
        assertEquals(1, service.accrueForTenant(tenant, SATURDAY));
    }

    @Test @DisplayName("a weekdays-only contract accrues on Friday and not on Saturday")
    void weekdaysOnlyContract() {
        weekdaysOnly();
        assertEquals(1, service.accrueForTenant(tenant, FRIDAY));
        assertEquals(0, service.accrueForTenant(tenant, SATURDAY));
    }
}
