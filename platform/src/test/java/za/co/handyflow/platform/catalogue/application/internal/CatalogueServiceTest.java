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
    @Mock CatalogueStructureService structure;
    @Mock CataloguePricingService pricing;
    CatalogueService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueService(items, categories, audit, new VatRateProvider(new BigDecimal("15.00")), structure, pricing);
        lenient().when(structure.resolveUnit(any(), anyString(), any())).thenAnswer(i -> ((String) i.getArgument(1)).trim());
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
        when(categories.nameTaken(eq(TENANT), eq("Tools"), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(TENANT, new CreateCategoryRequest("Tools", null), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("A category named 'Tools' already exists at the top level.");
    }

    @Test
    void vatExemptStoresAZeroRate() {
        var r = new CreateItemRequest("Bread", null, null, "each", BigDecimal.ONE, new BigDecimal("15"), null, null, null, null, null, true, null, null);
        var out = service.createItem(TENANT, r, ADMIN);
        assertThat(out.vatExempt()).isTrue();
        assertThat(out.vatRate()).isEqualByComparingTo("0");
    }

    // ── Slice 2: tree, attributes ─────────────────────────────────────────────

    private CatalogueCategory cat(String name, CatalogueCategory parent) {
        return CatalogueCategory.create(TENANT, name, null, parent == null ? null : parent.getId());
    }

    @Test
    void aCategoryCannotBeMovedInsideItsOwnSubCategory() {
        var root = cat("Building", null);
        var child = cat("Cement", root);
        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of(root, child));
        when(categories.findActiveById(TENANT, root.getId())).thenReturn(Optional.of(root));
        assertThatThrownBy(() -> service.updateCategory(TENANT, root.getId(), new CreateCategoryRequest("Building", null, child.getId()), ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A category cannot be moved inside itself or one of its own sub-categories.");
        assertThat(root.getParentId()).isNull();
    }

    @Test
    void movingACategoryChangesItsParentAndIsRecorded() {
        var a = cat("Building", null);
        var b = cat("Tools", null);
        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of(a, b));
        when(categories.findActiveById(TENANT, b.getId())).thenReturn(Optional.of(b));
        var out = service.updateCategory(TENANT, b.getId(), new CreateCategoryRequest("Tools", null, a.getId()), ADMIN);
        assertThat(b.getParentId()).isEqualTo(a.getId());
        assertThat(out.path()).isEqualTo("Building › Tools");
        assertThat(out.depth()).isEqualTo(2);
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(ev.capture());
        assertThat(ev.getValue().getDetail()).isEqualTo("moved: top level -> Building");
    }

    @Test
    void aCategoryUnderAnUnknownParentIsNotFound() {
        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of());
        assertThatThrownBy(() -> service.createCategory(TENANT, new CreateCategoryRequest("Tools", null, UUID.randomUUID()), ADMIN))
                .isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
    }

    @Test
    void aSiblingWithTheSameNameIsAConflictButOtherParentsAreFine() {
        var root = cat("Building", null);
        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of(root));
        when(categories.nameTaken(eq(TENANT), eq("Other"), eq(root.getId()), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(TENANT, new CreateCategoryRequest("Other", null, root.getId()), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("A category named 'Other' already exists in that category.");
        assertThat(service.createCategory(TENANT, new CreateCategoryRequest("Other", null, null), ADMIN).parentId()).isNull();
    }

    @Test
    void aCategoryWithSubCategoriesOrAttributesIsNotDeleted() {
        var root = cat("Building", null);
        var child = cat("Cement", root);
        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of(root, child));
        when(categories.findActiveById(TENANT, root.getId())).thenReturn(Optional.of(root));
        assertThatThrownBy(() -> service.deleteCategory(TENANT, root.getId(), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("This category still has 1 sub-category. Move or delete them first.");

        when(categories.findAllActive(TENANT)).thenReturn(java.util.List.of(root));
        when(structure.countDefinitionsFor(TENANT, root.getId())).thenReturn(2L);
        assertThatThrownBy(() -> service.deleteCategory(TENANT, root.getId(), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("This category still has 2 attributes. Delete or re-home them first.");
        assertThat(root.isDeleted()).isFalse();
    }

    @Test
    void anAttributeProblemStopsTheItemFromBeingSaved() {
        when(structure.check(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenThrow(new IllegalArgumentException("Fill in the required attribute: Colour."));
        assertThatThrownBy(() -> service.createItem(TENANT, req("Paint", null, "10", null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fill in the required attribute: Colour.");
        verify(items, never()).save(any());
        verify(structure, never()).store(any(), any(), any());
    }

    @Test
    void aDraftDoesNotNeedItsRequiredAttributesButAnActiveItemDoes() {
        var draft = new CreateItemRequest("Paint", null, null, "each", BigDecimal.TEN, null, null, null, null, null, null, null, CatalogueItemStatus.DRAFT, null);
        service.createItem(TENANT, draft, ADMIN);
        verify(structure).check(eq(TENANT), any(), any(), eq(false), any());
        service.createItem(TENANT, req("Paint 2", null, "10", null), ADMIN);
        verify(structure).check(eq(TENANT), any(), any(), eq(true), any());
    }

    @Test
    void activatingAnItemChecksItsRequiredAttributes() {
        var item = existing("Cement", "C-1", "100.00");
        item.changeStatus(CatalogueItemStatus.DRAFT, null);
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(structure.check(any(), any(), any(), eq(true), any()))
                .thenThrow(new IllegalArgumentException("Fill in the required attribute: Grade."));
        assertThatThrownBy(() -> service.changeStatus(TENANT, item.getId(), new ItemStatusRequest(CatalogueItemStatus.ACTIVE, null, null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fill in the required attribute: Grade.");
    }

    @Test
    void theUnitIsTheTenantsOwnSpellingFromTheUnitList() {
        when(structure.resolveUnit(eq(TENANT), eq("EACH"), any())).thenReturn("each");
        var r = new CreateItemRequest("Bolt", null, null, "EACH", BigDecimal.ONE, null, null, null, null, null, null, null, null, null);
        assertThat(service.createItem(TENANT, r, ADMIN).unit()).isEqualTo("each");
    }

    @Test
    void anUnknownUnitStopsTheSave() {
        when(structure.resolveUnit(any(), eq("bag"), any())).thenThrow(new IllegalArgumentException("The unit 'bag' is not set up. Choose a unit from the list."));
        var r = new CreateItemRequest("Sand", null, null, "bag", BigDecimal.ONE, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.createItem(TENANT, r, ADMIN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The unit 'bag' is not set up. Choose a unit from the list.");
        verify(items, never()).save(any());
    }

    @Test
    void aPriceEditedOnTheFormIsRecordedInThePriceHistory() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "120", null), ADMIN);
        verify(pricing).requireDirectEditAllowed(TENANT);
        verify(pricing).recordDirectChange(eq(TENANT), eq(item), eq(new BigDecimal("100.00")), eq(new BigDecimal("120.00")), eq(ADMIN));
    }

    @Test
    void whenApprovalIsRequiredAPriceCannotBeEditedOnTheForm() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Prices on this account change through a price change request, which needs approval. Use Change price on the item."))
                .when(pricing).requireDirectEditAllowed(TENANT);
        assertThatThrownBy(() -> service.updateItem(TENANT, item.getId(), req("Cement", "C-1", "120", null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Use Change price");
        assertThat(item.getDefaultPrice()).isEqualByComparingTo("100.00");
        verify(items, never()).save(any());
    }

    @Test
    void editingOtherFieldsNeverTouchesThePriceRules() {
        var item = existing("Cement", "C-1", "100.00");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        service.updateItem(TENANT, item.getId(), req("Cement 50kg", "C-1", "100", null), ADMIN);
        verify(pricing, never()).requireDirectEditAllowed(any());
        verify(pricing, never()).recordDirectChange(any(), any(), any(), any(), any());
    }

    @Test
    void renamingAParentRenamesItsVariantsButKeepsTheirOwnSkuAndPrice() {
        var item = existing("Tee shirt", "TEE", "100.00");
        var v = existing("Tee shirt — S / Red", "TEE-S-RED", "90.00");
        v.asVariant(item.getId(), "size=s|colour=red", "S / Red", "S|Red");
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(items.findVariants(TENANT, item.getId())).thenReturn(java.util.List.of(v));
        service.updateItem(TENANT, item.getId(), req("Tee", "TEE", "100", null), ADMIN);
        assertThat(v.getName()).isEqualTo("Tee — S / Red");
        assertThat(v.getSku()).isEqualTo("TEE-S-RED");
        assertThat(v.getDefaultPrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void aVariantCannotBeActivatedWhileItsParentIsNotActive() {
        var parent = existing("Tee shirt", "TEE", "100.00");
        parent.changeStatus(CatalogueItemStatus.SUSPENDED, "stock");
        var v = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Tee — S", null, "T-S", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("10.00"), null, new BigDecimal("15.00"), false), CatalogueItemStatus.DRAFT);
        v.asVariant(parent.getId(), "size=s", "S", "S");
        when(items.findActiveById(TENANT, parent.getId())).thenReturn(Optional.of(parent));
        when(items.findActiveById(TENANT, v.getId())).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.changeStatus(TENANT, v.getId(), new ItemStatusRequest(CatalogueItemStatus.ACTIVE, null, null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Activate the main item 'Tee shirt' first.");
    }

    @Test
    void suspendingAParentSuspendsItsActiveVariantsAndSkipsThoseThatCannotMove() {
        var parent = existing("Tee shirt", "TEE", "100.00");
        var active = existing("Tee — S", "T-S", "10.00");
        active.asVariant(parent.getId(), "size=s", "S", "S");
        var draft = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Tee — M", null, "T-M", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("10.00"), null, new BigDecimal("15.00"), false), CatalogueItemStatus.DRAFT);
        draft.asVariant(parent.getId(), "size=m", "M", "M");
        when(items.findActiveById(TENANT, parent.getId())).thenReturn(Optional.of(parent));
        when(items.findVariants(TENANT, parent.getId())).thenReturn(java.util.List.of(active, draft));
        service.changeStatus(TENANT, parent.getId(), new ItemStatusRequest(CatalogueItemStatus.SUSPENDED, "out of stock", null), ADMIN);
        assertThat(parent.getStatus()).isEqualTo(CatalogueItemStatus.SUSPENDED);
        assertThat(active.getStatus()).isEqualTo(CatalogueItemStatus.SUSPENDED);
        assertThat(draft.getStatus()).isEqualTo(CatalogueItemStatus.DRAFT);
    }

    @Test
    void deletingAParentDeletesItsVariants() {
        var parent = existing("Tee shirt", "TEE", "100.00");
        var v = existing("Tee — S", "T-S", "10.00");
        v.asVariant(parent.getId(), "size=s", "S", "S");
        when(items.findActiveById(TENANT, parent.getId())).thenReturn(Optional.of(parent));
        when(items.findVariants(TENANT, parent.getId())).thenReturn(java.util.List.of(v));
        service.softDeleteItem(TENANT, parent.getId(), ADMIN);
        assertThat(parent.isDeleted()).isTrue();
        assertThat(v.isDeleted()).isTrue();
    }

    @Test
    void theListCarriesTheVariantCountOfEachParent() {
        var parent = existing("Tee shirt", "TEE", "100.00");
        when(items.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(parent)));
        when(items.variantCounts(eq(TENANT), any())).thenReturn(java.util.List.<Object[]>of(new Object[]{parent.getId(), 3L}));
        var page = service.listItems(TENANT, new CatalogueService.ItemFilter(null, null, null, null), 0, 25, "name", false, ADMIN);
        assertThat(page.getContent().get(0).variantCount()).isEqualTo(3L);
    }
    // ── Item approval ──

    private static final Caller APPROVER = new Caller(UUID.randomUUID(), false, false, false, false, true);

    @Test
    void whenItemsNeedApprovalANewItemFromSomeoneWhoCannotApproveIsADraft() {
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(true);
        assertThat(service.createItem(TENANT, req("Cement", "C-1", "100", null), CLERK).status()).isEqualTo("DRAFT");
    }

    @Test
    void whenItemsNeedApprovalAskingForActiveIsRefusedButAnApproverMay() {
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(true);
        var active = new CreateItemRequest("A", null, null, "each", BigDecimal.ONE, null, null, null, null, null, null, null, CatalogueItemStatus.ACTIVE, null);
        assertThatThrownBy(() -> service.createItem(TENANT, active, CLERK)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("need approval");
        assertThat(service.createItem(TENANT, req("Sand", "S-1", "5", null), APPROVER).status()).isEqualTo("ACTIVE");
    }

    @Test
    void withoutTheSettingNothingChanges() {
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(false);
        assertThat(service.createItem(TENANT, req("Cement", "C-1", "100", null), CLERK).status()).isEqualTo("ACTIVE");
    }

    @Test
    void aDraftCannotBeActivatedDirectlyWhenApprovalIsRequired() {
        var draft = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Brick", null, "B-1", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("5.00"), null, new BigDecimal("15.00"), false), CatalogueItemStatus.DRAFT);
        when(items.findActiveById(TENANT, draft.getId())).thenReturn(Optional.of(draft));
        when(pricing.itemApprovalRequired(TENANT)).thenReturn(true);
        assertThatThrownBy(() -> service.changeStatus(TENANT, draft.getId(), new ItemStatusRequest(CatalogueItemStatus.ACTIVE, null, null), CLERK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs approval");
        assertThat(service.changeStatus(TENANT, draft.getId(), new ItemStatusRequest(CatalogueItemStatus.ACTIVE, null, null), APPROVER).status()).isEqualTo("ACTIVE");
    }
}
