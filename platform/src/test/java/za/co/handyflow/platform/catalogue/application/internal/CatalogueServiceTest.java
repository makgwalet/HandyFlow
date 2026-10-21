package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.CreateCategoryRequest;
import za.co.handyflow.platform.catalogue.dto.CreateItemRequest;
import za.co.handyflow.platform.catalogue.dto.ItemStatusRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), true, true, true);
    private static final Caller CLERK = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueAuditEventRepository audit;
    CatalogueService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueService(items, categories, audit, new VatRateProvider(new BigDecimal("15.00")));
        lenient().when(items.nameTaken(any(), anyString(), any())).thenReturn(false);
        lenient().when(items.skuTaken(any(), anyString(), any())).thenReturn(false);
        lenient().when(items.barcodeTaken(any(), anyString(), any())).thenReturn(false);
    }

    private static CreateItemRequest req(String name, String sku, String price, String cost) {
        return new CreateItemRequest(name, null, null, "each", new BigDecimal(price), null, sku, null, null, null,
                cost == null ? null : new BigDecimal(cost), null, null, null);
    }

    private static CatalogueItem existing(String name, String sku, String price) {
        var d = new CatalogueItem.Details(name, null, sku, null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal(price), new BigDecimal("60.00"), new BigDecimal("15.00"), false);
        return CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
    }

    @Test
    void createCleansTheSkuAndWritesAHistoryLine() {
        var out = service.createItem(TENANT, req(" Cement ", " c-1 ", "100", null), ADMIN);
        assertThat(out.name()).isEqualTo("Cement");
        assertThat(out.sku()).isEqualTo("C-1");
        assertThat(out.status()).isEqualTo("ACTIVE");
        assertThat(out.vatRate()).isEqualByComparingTo("15");
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.CREATED);
        assertThat(ev.getValue().getActorId()).isEqualTo(ADMIN.userId());
    }

    @Test
    void duplicateNameSkuAndBarcodeAreConflicts() {
        when(items.nameTaken(eq(TENANT), eq("Cement"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createItem(TENANT, req("Cement", null, "1", null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("An item named 'Cement' already exists.");

        when(items.skuTaken(eq(TENANT), eq("C-1"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createItem(TENANT, req("Sand", "c-1", "1", null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("Another item already uses the SKU 'C-1'.");

        when(items.barcodeTaken(eq(TENANT), eq("600"), any())).thenReturn(true);
        var r = new CreateItemRequest("Stone", null, null, "each", BigDecimal.ONE, null, null, "600", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.createItem(TENANT, r, ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("Another item already uses the barcode '600'.");
        verify(items, never()).save(any());
    }

    @Test
    void aNewItemCannotStartSuspended() {
        var r = new CreateItemRequest("A", null, null, "each", BigDecimal.ONE, null, null, null, null, null, null, null, CatalogueItemStatus.SUSPENDED, null);
        assertThatThrownBy(() -> service.createItem(TENANT, r, ADMIN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A new item can only start as a draft or active.");
    }

    @Test
    void settingTheCostNeedsThePricePermission() {
        assertThatThrownBy(() -> service.createItem(TENANT, req("A", null, "10", "5"), CLERK))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.createItem(TENANT, req("A", null, "10", null), CLERK).costPrice()).isNull();
    }

    @Test
    void costAndMarginAreHiddenFromCallersWithoutPriceAccess() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        var seen = service.getItem(TENANT, item.getId(), ADMIN);
        assertThat(seen.costPrice()).isEqualByComparingTo("60");
        assertThat(seen.marginPercent()).isEqualByComparingTo("40.0");
        var hidden = service.getItem(TENANT, item.getId(), CLERK);
        assertThat(hidden.costPrice()).isNull();
        assertThat(hidden.marginPercent()).isNull();
    }

    @Test
    void aPriceEditIsRecordedAsAPriceChangeWithOldAndNewValues() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "120", "60"), ADMIN);
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(CatalogueAuditAction.PRICE_CHANGED);
        assertThat(ev.getValue().getDetail()).isEqualTo("price: 100.00 -> 120.00");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("120");
    }

    @Test
    void anEditThatChangesNothingWritesNoHistory() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "100", "60"), ADMIN);
        verify(audit, never()).save(any());
        verify(items, never()).save(any());
    }

    @Test
    void anEditCannotChangeTheCostWithoutThePricePermissionButKeepsIt() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "100", "10"), CLERK))
                .isInstanceOf(AccessDeniedException.class);
        service.updateItem(TENANT, item.getId(), req("Cement 50kg", "C-1", "100", null), CLERK);
        assertThat(item.getCostPrice()).isEqualByComparingTo("60");
        assertThat(item.getName()).isEqualTo("Cement 50kg");
    }

    @Test
    void aStaleVersionIsRefused() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        var stale = new CreateItemRequest("Cement", null, null, "each", new BigDecimal("100"), null, "C-1", null, null, null, null, null, null, 7L);
        // a freshly created entity has a null version, so any number the client sends is not the current one
        assertThatThrownBy(() -> service.updateItem(TENANT, item.getId(), stale, ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("This item was changed by someone else. Reload it and try again.");
    }

    @Test
    void anArchivedItemCannotBeEdited() {
        var item = existing("Cement", "C-1", "100.00");
        item.changeStatus(CatalogueItemStatus.ARCHIVED, "old");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "100", null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("An archived item cannot be edited. Restore it to a draft first.");
    }

    @Test
    void discontinuingNeedsAReasonAndTheArchivePermission() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.changeStatus(TENANT, item.getId(), new ItemStatusRequest(CatalogueItemStatus.DISCONTINUED, "range ended", null), CLERK))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.changeStatus(TENANT, item.getId(), new ItemStatusRequest(CatalogueItemStatus.DISCONTINUED, " ", null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Say why (at least 3 characters).");
        var out = service.changeStatus(TENANT, item.getId(), new ItemStatusRequest(CatalogueItemStatus.DISCONTINUED, "range ended", null), ADMIN);
        assertThat(out.status()).isEqualTo("DISCONTINUED");
        assertThat(out.statusReason()).isEqualTo("range ended");
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void suspendingNeedsOnlyTheWritePermission() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        assertThat(service.changeStatus(TENANT, item.getId(), new ItemStatusRequest(CatalogueItemStatus.SUSPENDED, "no stock", null), CLERK).status())
                .isEqualTo("SUSPENDED");
    }

    @Test
    void deleteRecordsWhoDidIt() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        service.softDeleteItem(TENANT, item.getId(), ADMIN);
        assertThat(item.getDeletedBy()).isEqualTo(ADMIN.userId());
        assertThat(item.isDeleted()).isTrue();
        assertThat(item.getStatus()).isEqualTo(CatalogueItemStatus.ARCHIVED);
    }

    @Test
    void aCategoryWithItemsIsNotDeleted() {
        var cat = CatalogueCategory.create(TENANT, "Tools", null);
        when(categories.findActiveById(TENANT, cat.getId())).thenReturn(Optional.of(cat));
        when(items.countLiveInCategory(TENANT, cat)).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteCategory(TENANT, cat.getId(), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("This category still has 3 items. Move or delete them first.");
        assertThat(cat.isDeleted()).isFalse();

        when(items.countLiveInCategory(TENANT, cat)).thenReturn(0L);
        service.deleteCategory(TENANT, cat.getId(), ADMIN);
        assertThat(cat.isDeleted()).isTrue();
        assertThat(cat.getDeletedBy()).isEqualTo(ADMIN.userId());
    }

    @Test
    void duplicateCategoryNamesAreConflicts() {
        when(categories.nameTaken(eq(TENANT), eq("Tools"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(TENANT, new CreateCategoryRequest("Tools", null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("A category named 'Tools' already exists.");
    }

    @Test
    void vatExemptStoresAZeroRate() {
        var r = new CreateItemRequest("Bread", null, null, "each", BigDecimal.ONE, new BigDecimal("15"), null, null, null, null, null, true, null, null);
        var out = service.createItem(TENANT, r, ADMIN);
        assertThat(out.vatExempt()).isTrue();
        assertThat(out.vatRate()).isEqualByComparingTo("0");
    }
}
