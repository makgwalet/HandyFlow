package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueVariantServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), true, true, true);
    private static final Caller CLERK = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueVariantOptionRepository options;
    @Mock CatalogueAuditEventRepository audit;
    @Mock CatalogueService catalogue;
    CatalogueVariantService service;
    CatalogueItem parent;

    @BeforeEach
    void setUp() {
        service = new CatalogueVariantService(items, options, audit, catalogue);
        parent = item("Tee shirt", "TEE", "100.00", CatalogueItemStatus.ACTIVE);
        when(items.findActiveById(TENANT, parent.getId())).thenReturn(Optional.of(parent));
        when(items.findVariants(TENANT, parent.getId())).thenReturn(List.of());
        when(options.findForParent(TENANT, parent.getId())).thenReturn(List.of());
    }

    private static CatalogueItem item(String name, String sku, String price, CatalogueItemStatus status) {
        var d = new CatalogueItem.Details(name, "A shirt", sku, null, "Acme", CatalogueItemType.PRODUCT, "each",
                new BigDecimal(price), new BigDecimal("40.00"), new BigDecimal("15.00"), false);
        return CatalogueItem.create(TENANT, null, d, status);
    }

    private static CreateVariantsRequest request(List<VariantOptionDto> opts, VariantRowRequest... rows) {
        return new CreateVariantsRequest(opts, List.of(rows));
    }

    private static final List<VariantOptionDto> SIZE_COLOUR = List.of(new VariantOptionDto("Size", List.of("S", "M")), new VariantOptionDto("Colour", List.of("Red", "Blue")));

    private static VariantRowRequest row(String size, String colour, String sku, String price) {
        return new VariantRowRequest(List.of(size, colour), sku, null, price == null ? null : new BigDecimal(price), null);
    }

    private List<CatalogueItem> savedItems() {
        var c = ArgumentCaptor.forClass(CatalogueItem.class);
        verify(items, org.mockito.Mockito.atLeastOnce()).save(c.capture());
        return new ArrayList<>(c.getAllValues());
    }

    @Test
    void eachVariantIsAnItemWithItsOwnNameSkuPriceAndTheParentsFields() {
        service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", "tee-s-red", "90"), row("m", "blue", null, null)), ADMIN);
        var v = savedItems();
        assertThat(v.size()).isEqualTo(2);
        assertThat(v.get(0).getName()).isEqualTo("Tee shirt — S / Red");
        assertThat(v.get(0).getSku()).isEqualTo("TEE-S-RED");
        assertThat(v.get(0).getDefaultPrice()).isEqualByComparingTo("90.00");
        assertThat(v.get(0).getParentId()).isEqualTo(parent.getId());
        assertThat(v.get(0).getBrand()).isEqualTo("Acme");
        assertThat(v.get(0).getUnit()).isEqualTo("each");
        assertThat(v.get(0).getVatRate()).isEqualByComparingTo("15.00");
        assertThat(v.get(0).getVariantKey()).isEqualTo("size=s|colour=red");
        assertThat(v.get(0).getStatus()).isEqualTo(CatalogueItemStatus.ACTIVE);
        assertThat(v.get(1).getName()).isEqualTo("Tee shirt — M / Blue");
        assertThat(v.get(1).getDefaultPrice()).isEqualByComparingTo("100.00");
        assertThat(v.get(1).getSku()).isNull();
    }

    @Test
    void theOptionsAreStoredWithAllTheirValues() {
        service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", null, null)), ADMIN);
        var o = ArgumentCaptor.forClass(CatalogueVariantOption.class);
        verify(options, org.mockito.Mockito.times(2)).save(o.capture());
        assertThat(o.getAllValues().get(0).getName()).isEqualTo("Size");
        assertThat(o.getAllValues().get(0).values().size()).isEqualTo(2);
        assertThat(o.getAllValues().get(1).getName()).isEqualTo("Colour");
    }

    @Test
    void variantsOfADraftOrSuspendedParentStartAsDrafts() {
        var draft = item("Hat", "HAT", "50.00", CatalogueItemStatus.DRAFT);
        when(items.findActiveById(TENANT, draft.getId())).thenReturn(Optional.of(draft));
        when(items.findVariants(TENANT, draft.getId())).thenReturn(List.of());
        when(options.findForParent(TENANT, draft.getId())).thenReturn(List.of());
        service.create(TENANT, draft.getId(), request(List.of(new VariantOptionDto("Size", List.of("S"))), new VariantRowRequest(List.of("S"), null, null, null, null)), ADMIN);
        assertThat(savedItems().get(0).getStatus()).isEqualTo(CatalogueItemStatus.DRAFT);
    }

    @Test
    void aCombinationThatExistsIsRefusedAndNothingIsSaved() {
        var first = item("Tee shirt — S / Red", "A", "100", CatalogueItemStatus.ACTIVE);
        first.asVariant(parent.getId(), "size=s|colour=red", "S / Red", "S|Red");
        when(items.findVariants(TENANT, parent.getId())).thenReturn(List.of(first));
        when(options.findForParent(TENANT, parent.getId())).thenReturn(List.of(
                CatalogueVariantOption.create(TENANT, parent.getId(), "Size", 0, List.of("S", "M")),
                CatalogueVariantOption.create(TENANT, parent.getId(), "Colour", 1, List.of("Red", "Blue"))));
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("s", "RED", null, null)), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("The variant S / Red already exists.");
        verify(items, never()).save(any());
    }

    @Test
    void laterCallsMustUseTheSameOptionsButMayAddValues() {
        var first = item("Tee shirt — S / Red", "A", "100", CatalogueItemStatus.ACTIVE);
        first.asVariant(parent.getId(), "size=s|colour=red", "S / Red", "S|Red");
        when(items.findVariants(TENANT, parent.getId())).thenReturn(List.of(first));
        when(options.findForParent(TENANT, parent.getId())).thenReturn(List.of(
                CatalogueVariantOption.create(TENANT, parent.getId(), "Size", 0, List.of("S", "M")),
                CatalogueVariantOption.create(TENANT, parent.getId(), "Colour", 1, List.of("Red", "Blue"))));
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(List.of(new VariantOptionDto("Material", List.of("Cotton"))),
                new VariantRowRequest(List.of("Cotton"), null, null, null, null)), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has the options Size, Colour");
        var more = List.of(new VariantOptionDto("size", List.of("L")), new VariantOptionDto("colour", List.of("Green")));
        service.create(TENANT, parent.getId(), request(more, new VariantRowRequest(List.of("L", "Green"), null, null, null, null)), ADMIN);
        assertThat(savedItems().get(0).getVariantLabel()).isEqualTo("L / Green");
    }

    @Test
    void aValueTheOptionDoesNotOfferIsRefused() {
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("XL", "Red", null, null)), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("'XL' is not a value of the option 'Size'.");
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, new VariantRowRequest(List.of("S"), null, null, null, null)), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Choose one value for each option.");
    }

    @Test
    void duplicateSkuBarcodeAndNameAreConflictsWithinTheRequestAndAgainstOtherItems() {
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", "X1", null), row("M", "Red", "x1", null)), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("Another item already uses the SKU 'X1'.");
        when(items.skuTaken(eq(TENANT), eq("TAKEN"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", "taken", null)), ADMIN))
                .isInstanceOf(ConflictException.class);
        when(items.nameTaken(eq(TENANT), eq("Tee shirt — S / Red"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", null, null)), ADMIN))
                .isInstanceOf(ConflictException.class).hasMessage("An item named 'Tee shirt — S / Red' already exists.");
        verify(items, never()).save(any());
    }

    @Test
    void aVariantCannotHaveVariantsAndAnArchivedItemCannotGetThem() {
        var v = item("Tee — S", "T-S", "10", CatalogueItemStatus.ACTIVE);
        v.asVariant(parent.getId(), "size=s", "S", "S");
        when(items.findActiveById(TENANT, v.getId())).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.create(TENANT, v.getId(), request(List.of(new VariantOptionDto("Size", List.of("S"))), new VariantRowRequest(List.of("S"), null, null, null, null)), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A variant cannot have variants of its own.");
        parent.changeStatus(CatalogueItemStatus.ARCHIVED, "old");
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", null, null)), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("archived");
    }

    @Test
    void aRequestWithNoVariantsOrNoOptionsIsRefused() {
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), new CreateVariantsRequest(SIZE_COLOUR, List.of()), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Choose at least one variant to create.");
        assertThatThrownBy(() -> service.create(TENANT, parent.getId(), new CreateVariantsRequest(List.of(), List.of(row("S", "Red", null, null))), ADMIN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Add at least one option, for example Size.");
    }

    @Test
    void costIsOnlyStoredForSomeoneWhoMayWritePrices() {
        var withCost = new VariantRowRequest(List.of("S", "Red"), null, null, null, new BigDecimal("30"));
        service.create(TENANT, parent.getId(), request(SIZE_COLOUR, withCost), CLERK);
        assertThat(savedItems().get(0).getCostPrice()).isNull();
    }

    @Test
    void creatingVariantsWritesAHistoryLinePerVariantAndOneOnTheParent() {
        service.create(TENANT, parent.getId(), request(SIZE_COLOUR, row("S", "Red", null, null), row("M", "Red", null, null)), ADMIN);
        var ev = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit, org.mockito.Mockito.times(3)).save(ev.capture());
        assertThat(ev.getAllValues().get(2).getDetail()).isEqualTo("2 variants added");
    }

    @Test
    void viewListsOptionsAndVariantsAndHidesCostFromThoseWhoCannotSeePrices() {
        var v = item("Tee shirt — S / Red", "TEE-S-RED", "90", CatalogueItemStatus.ACTIVE);
        v.asVariant(parent.getId(), "size=s|colour=red", "S / Red", "S|Red");
        when(items.findVariants(TENANT, parent.getId())).thenReturn(List.of(v));
        when(options.findForParent(TENANT, parent.getId())).thenReturn(List.of(CatalogueVariantOption.create(TENANT, parent.getId(), "Size", 0, List.of("S", "M"))));
        var admin = service.view(TENANT, parent.getId(), ADMIN);
        assertThat(admin.options().get(0).name()).isEqualTo("Size");
        assertThat(admin.variants().get(0).values().size()).isEqualTo(2);
        assertThat(admin.variants().get(0).costPrice()).isEqualByComparingTo("40.00");
        assertThat(service.view(TENANT, parent.getId(), CLERK).variants().get(0).costPrice()).isNull();
    }

    @Test
    void editingAVariantGoesThroughTheNormalItemUpdate() {
        var v = item("Tee shirt — S / Red", "OLD", "90", CatalogueItemStatus.ACTIVE);
        v.asVariant(parent.getId(), "size=s|colour=red", "S / Red", "S|Red");
        when(items.findActiveById(TENANT, v.getId())).thenReturn(Optional.of(v));
        service.update(TENANT, parent.getId(), v.getId(), new UpdateVariantRequest("new-1", "600", new BigDecimal("95"), null, 3L), ADMIN);
        var req = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue).updateItem(eq(TENANT), eq(v.getId()), req.capture(), eq(ADMIN));
        assertThat(req.getValue().name()).isEqualTo("Tee shirt — S / Red");
        assertThat(req.getValue().sku()).isEqualTo("new-1");
        assertThat(req.getValue().defaultPrice()).isEqualByComparingTo("95");
        assertThat(req.getValue().costPrice()).isEqualByComparingTo("40.00");
        assertThat(req.getValue().version()).isEqualTo(3L);
    }

    @Test
    void aVariantOfAnotherParentIsNotFound() {
        var v = item("Other — S", "O-S", "10", CatalogueItemStatus.ACTIVE);
        v.asVariant(UUID.randomUUID(), "size=s", "S", "S");
        when(items.findActiveById(TENANT, v.getId())).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.update(TENANT, parent.getId(), v.getId(), new UpdateVariantRequest(null, null, null, null, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogue, never()).updateItem(any(), any(), any(), any());
    }
}
