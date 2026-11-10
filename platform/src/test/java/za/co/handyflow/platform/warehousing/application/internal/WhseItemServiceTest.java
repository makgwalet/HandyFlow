package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseItemDetails;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseItemServiceTest {

    private static final String EAN = "4006381333931";
    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private WhseItemRepository repo;
    private WhseClientService clients;
    private JdbcTemplate jdbc;
    private WhseItemService service;

    @BeforeEach
    void setUp() {
        repo = mock(WhseItemRepository.class);
        clients = mock(WhseClientService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new WhseItemService(repo, clients, jdbc);
        when(repo.save(any(WhseItem.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findActiveByClientAndSku(any(), any(), any())).thenReturn(Optional.empty());
        when(repo.findActiveByClientAndBarcode(any(), any(), any())).thenReturn(Optional.empty());
    }

    private TenantId t() { return TenantId.of(tenant); }

    private WhseItemDetails details(String barcode, String cost) {
        return new WhseItemDetails(barcode, null, null, null, null, cost == null ? null : new BigDecimal(cost), null, null, "AMBIENT", false, false, true);
    }

    @Test @DisplayName("a new item stores its master data")
    void createStoresDetails() {
        WhseItem i = service.create(t(), client, "SKU1", "Widget", "EACH", null, details(EAN, "12.50"), actor);
        assertEquals(EAN, i.getBarcode());
        assertEquals(0, new BigDecimal("12.50").compareTo(i.getUnitCost()));
    }

    @Test @DisplayName("the same barcode on another item of the same client is a conflict")
    void duplicateBarcode() {
        WhseItem other = WhseItem.create(tenant, client, "OTHER", "Other", "EACH", null);
        when(repo.findActiveByClientAndBarcode(tenant, client, EAN)).thenReturn(Optional.of(other));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.create(t(), client, "SKU1", "Widget", "EACH", null, details(EAN, null), actor));
        assertEquals("DUPLICATE_BARCODE", e.getErrorCode());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("an item keeping its own barcode on update is not a conflict")
    void sameItemBarcode() {
        WhseItem item = WhseItem.create(tenant, client, "SKU1", "Widget", "EACH", null);
        item.applyDetails(details(EAN, null));
        when(repo.findActiveById(tenant, item.getId())).thenReturn(Optional.of(item));
        when(repo.findActiveByClientAndBarcode(tenant, client, EAN)).thenReturn(Optional.of(item));
        assertDoesNotThrow(() -> service.update(t(), item.getId(), "Widget", "EACH", null, details(EAN, null), actor));
    }

    @Test @DisplayName("changing the cost is audited; changing only the description is not")
    void costChangeAudited() {
        WhseItem item = WhseItem.create(tenant, client, "SKU1", "Widget", "EACH", null);
        item.applyDetails(details(null, "10"));
        when(repo.findActiveById(tenant, item.getId())).thenReturn(Optional.of(item));

        service.update(t(), item.getId(), "Widget renamed", "EACH", null, details(null, "10.0000"), actor);
        verifyNoInteractions(jdbc);

        service.update(t(), item.getId(), "Widget renamed", "EACH", null, details(null, "11"), actor);
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
    }

    @Test @DisplayName("changing only the cost currency is audited, and the same cost in a new currency is a different cost")
    void currencyChangeAudited() {
        WhseItem item = WhseItem.create(tenant, client, "SKU1", "Widget", "EACH", null);
        item.applyDetails(details(null, "10"));
        when(repo.findActiveById(tenant, item.getId())).thenReturn(Optional.of(item));

        WhseItemDetails usd = new WhseItemDetails(null, null, null, null, null, new BigDecimal("10"), null, null, "AMBIENT", false, false, true, "usd");
        WhseItem saved = service.update(t(), item.getId(), "Widget", "EACH", null, usd, actor);

        assertEquals("USD", saved.getUnitCostCurrency());
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
    }

    @Test @DisplayName("scanning an invalid barcode is refused before any lookup")
    void scanInvalid() {
        assertThrows(IllegalArgumentException.class, () -> service.findByBarcode(t(), "4006381333932", null));
        verify(repo, never()).findByBarcode(any(), any(), any());
    }
}
