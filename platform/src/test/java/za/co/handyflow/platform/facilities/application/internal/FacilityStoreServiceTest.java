package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Store management and the stock valuation report. */
class FacilityStoreServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final Map<UUID, FacilityStore> stores = new LinkedHashMap<>();
    private final List<FacilityStockLevel> levels = new ArrayList<>();
    private final Map<UUID, FacilityStockItem> items = new HashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> f) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = f.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityStoreRepository storeRepo = fake(FacilityStoreRepository.class, (n, a) -> switch (n) {
        case "save", "saveAndFlush" -> { FacilityStore s = (FacilityStore) a[0]; stores.put(s.getId(), s); yield s; }
        case "findDefault" -> stores.values().stream().filter(FacilityStore::isDefaultStore).findFirst();
        case "findByIdForTenant" -> Optional.ofNullable(stores.get(a[1]));
        case "findAllForTenant" -> new ArrayList<>(stores.values());
        case "codeExists" -> stores.values().stream().anyMatch(s -> s.getCode().equals(a[1]) && !s.getId().equals(a[2]));
        default -> NOPE;
    });
    private final FacilityStockLevelRepository levelRepo = fake(FacilityStockLevelRepository.class, (n, a) -> switch (n) {
        case "findAllPositive" -> levels.stream().filter(l -> l.getOnHand().signum() > 0).toList();
        case "countPositiveInStore" -> levels.stream().filter(l -> l.getStoreId().equals(a[1]) && l.getOnHand().signum() > 0).count();
        default -> NOPE;
    });
    private final FacilityStockItemRepository itemRepo = fake(FacilityStockItemRepository.class, (n, a) ->
            "findByIds".equals(n) ? items.values().stream().filter(i -> ((Collection<?>) a[1]).contains(i.getId())).toList() : NOPE);
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(site.getId().equals(a[1]) ? site : null) : NOPE);
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityStoreService service = new FacilityStoreService(storeRepo, levelRepo, itemRepo, siteRepo, auditService);
    private final FacilityStockValuationService valuation = new FacilityStockValuationService(levelRepo, itemRepo, storeRepo);

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private FacilityStockItem item(String sku, String cost) {
        FacilityStockItem i = FacilityStockItem.create(tenant, sku, "Item " + sku, "each", null, n("1"), n("0"));
        i.receive(n("1"), n(cost));   // sets the average cost
        i.issue(n("1"));
        items.put(i.getId(), i);
        return i;
    }

    private void hold(FacilityStore s, FacilityStockItem i, String qty) {
        FacilityStockLevel l = FacilityStockLevel.create(tenant, s.getId(), i.getId());
        l.add(n(qty));
        levels.add(l);
    }

    @Test
    void theFirstStoreBecomesTheDefaultAndLaterOnesDoNot() {
        StoreResponse a = service.create(tenant, new StoreRequest("main", "Main store", site.getId()));
        StoreResponse b = service.create(tenant, new StoreRequest("van1", "Van 1", null));
        assertTrue(a.defaultStore());
        assertEquals("MAIN", a.code());
        assertFalse(b.defaultStore());
        assertEquals(List.of("STORE_CREATED", "STORE_CREATED"), audit);
    }

    @Test
    void aDuplicateCodeOrUnknownSiteIsRefused() {
        service.create(tenant, new StoreRequest("MAIN", "Main", null));
        assertEquals("STORE_CODE_TAKEN", assertThrows(HandyFlowException.class,
                () -> service.create(tenant, new StoreRequest("main", "Again", null))).getErrorCode());
        assertThrows(ResourceNotFoundException.class, () -> service.create(tenant, new StoreRequest("X", "X", UUID.randomUUID())));
    }

    @Test
    void theCodeCannotBeChangedButTheNameCan() {
        StoreResponse a = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        assertThrows(IllegalArgumentException.class, () -> service.update(tenant, a.id(), new StoreRequest("OTHER", "Main", null)));
        assertEquals("Warehouse", service.update(tenant, a.id(), new StoreRequest("MAIN", "Warehouse", null)).name());
    }

    @Test
    void makingAnotherStoreTheDefaultMovesTheFlag() {
        StoreResponse a = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse b = service.create(tenant, new StoreRequest("VAN", "Van", null));
        service.makeDefault(tenant, b.id());
        assertFalse(stores.get(a.id()).isDefaultStore());
        assertTrue(stores.get(b.id()).isDefaultStore());
        assertEquals(1, stores.values().stream().filter(FacilityStore::isDefaultStore).count());
        assertTrue(audit.contains("STORE_DEFAULT_CHANGED"));
    }

    @Test
    void theDefaultStoreCannotBeDeactivated() {
        StoreResponse a = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        assertEquals("STORE_IS_DEFAULT", assertThrows(HandyFlowException.class, () -> service.setActive(tenant, a.id(), false)).getErrorCode());
    }

    @Test
    void aStoreHoldingStockCannotBeDeactivatedUntilItIsEmpty() {
        service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse van = service.create(tenant, new StoreRequest("VAN", "Van", null));
        FacilityStockItem i = item("S-1", "5.00");
        hold(stores.get(van.id()), i, "3");
        assertEquals("STORE_NOT_EMPTY", assertThrows(HandyFlowException.class, () -> service.setActive(tenant, van.id(), false)).getErrorCode());
        levels.get(0).remove(n("3"));
        assertFalse(service.setActive(tenant, van.id(), false).active());
        assertTrue(service.setActive(tenant, van.id(), true).active());
    }

    @Test
    void anInactiveStoreCannotBecomeTheDefault() {
        service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse van = service.create(tenant, new StoreRequest("VAN", "Van", null));
        service.setActive(tenant, van.id(), false);
        assertEquals("STORE_INACTIVE", assertThrows(HandyFlowException.class, () -> service.makeDefault(tenant, van.id())).getErrorCode());
    }

    @Test
    void listShowsItemLinesAndValuePerStore() {
        StoreResponse main = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse van = service.create(tenant, new StoreRequest("VAN", "Van", null));
        FacilityStockItem a = item("S-1", "5.00");
        FacilityStockItem b = item("S-2", "2.50");
        hold(stores.get(main.id()), a, "10");
        hold(stores.get(main.id()), b, "4");
        hold(stores.get(van.id()), a, "2");
        Map<String, StoreResponse> byCode = new HashMap<>();
        service.list(tenant).forEach(r -> byCode.put(r.code(), r));
        assertEquals(2, byCode.get("MAIN").itemLines());
        assertEquals(0, n("60.00").compareTo(byCode.get("MAIN").stockValue()));
        assertEquals(0, n("10.00").compareTo(byCode.get("VAN").stockValue()));
    }

    @Test
    void valuationTotalsPerStoreAndOverall() {
        StoreResponse main = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse van = service.create(tenant, new StoreRequest("VAN", "Van", null));
        FacilityStockItem a = item("S-1", "5.00");
        FacilityStockItem b = item("S-2", "2.50");
        hold(stores.get(main.id()), a, "10");
        hold(stores.get(van.id()), b, "4");
        StockValuationResponse v = valuation.valuation(tenant, null);
        assertEquals(0, n("60.00").compareTo(v.totalValue()));
        assertEquals(2, v.byStore().size());
        assertEquals(2, v.lines().size());
        assertEquals("S-1", v.lines().get(0).sku());   // MAIN before VAN
    }

    @Test
    void valuationCanBeLimitedToOneStoreAndRefusesAnUnknownOne() {
        StoreResponse main = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        StoreResponse van = service.create(tenant, new StoreRequest("VAN", "Van", null));
        hold(stores.get(main.id()), item("S-1", "5.00"), "10");
        hold(stores.get(van.id()), item("S-2", "2.00"), "1");
        StockValuationResponse v = valuation.valuation(tenant, van.id());
        assertEquals(0, n("2.00").compareTo(v.totalValue()));
        assertEquals(1, v.lines().size());
        assertThrows(ResourceNotFoundException.class, () -> valuation.valuation(tenant, UUID.randomUUID()));
    }

    @Test
    void valuationIncludesInactiveItemsThatStillHoldStock() {
        StoreResponse main = service.create(tenant, new StoreRequest("MAIN", "Main", null));
        FacilityStockItem a = item("S-1", "5.00");
        hold(stores.get(main.id()), a, "2");
        a.deactivate();
        StockValuationResponse v = valuation.valuation(tenant, null);
        assertEquals(1, v.lines().size());
        assertFalse(v.lines().get(0).itemActive());
        assertEquals(0, n("10.00").compareTo(v.totalValue()));
    }
}
