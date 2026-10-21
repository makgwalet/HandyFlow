package za.co.handyflow.platform.catalogue.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueStructureService;
import za.co.handyflow.platform.catalogue.application.internal.CataloguePricingService;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueVariantService;
import za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.ItemFilter;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemType;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catalogue endpoints. SETTINGS_MANAGE still opens reading, editing and archiving, so existing roles keep their
 * access. It does NOT open prices: cost visibility and changes need CATALOGUE_PRICE_READ / CATALOGUE_PRICE_WRITE,
 * which are assigned explicitly (owner decision: managing settings is separate from commercial control).
 */
@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
@Tag(name = "Product Catalogue", description = "Manage your product and service catalogue")
public class CatalogueController {

    private static final String PICK = "hasAnyAuthority('INVOICE_CREATE','POS_READ','POS_MANAGE','POS_SELL','CATALOGUE_READ','CATALOGUE_WRITE','SETTINGS_MANAGE')";
    // Anyone who could see items before (invoicing, POS) can still see the list, even if their role predates CATALOGUE_READ.
    private static final String READ = "hasAnyAuthority('CATALOGUE_READ','CATALOGUE_WRITE','SETTINGS_MANAGE','INVOICE_CREATE','POS_READ','POS_MANAGE')";
    private static final String WRITE = "hasAnyAuthority('CATALOGUE_WRITE','SETTINGS_MANAGE')";
    private static final String ARCHIVE = "hasAnyAuthority('CATALOGUE_ARCHIVE','SETTINGS_MANAGE')";

    private static final String ADMIN = "hasAuthority('CATALOGUE_ADMIN')";
    private static final String PRICE_READ = "hasAuthority('CATALOGUE_PRICE_READ')";
    private static final String PRICE_WRITE = "hasAuthority('CATALOGUE_PRICE_WRITE')";
    private static final String PRICE_DECIDE = "hasAuthority('CATALOGUE_PRICE_APPROVE')";
    private static final String PRICE_QUEUE = "hasAnyAuthority('CATALOGUE_PRICE_APPROVE','CATALOGUE_PRICE_READ','CATALOGUE_PRICE_WRITE')";

    private final CatalogueService catalogueService;
    private final CatalogueStructureService structureService;
    private final CataloguePricingService pricingService;
    private final CatalogueVariantService variantService;

    /** What the signed-in user may do, from their authorities. */
    private static Caller caller(Authentication auth) {
        Set<String> a = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = a.contains("SETTINGS_MANAGE");
        return new Caller(currentUserOrNull(),
                a.contains("CATALOGUE_PRICE_READ"),
                a.contains("CATALOGUE_PRICE_WRITE"),
                admin || a.contains("CATALOGUE_ARCHIVE"),
                a.contains("CATALOGUE_PRICE_APPROVE"));
    }

    /** Read-only "view as" tokens carry no user id; reads still work and writes are not allowed for them anyway. */
    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (IllegalStateException e) { return null; }
    }

    // ── Items ────────────────────────────────────────────────────────────────

    @GetMapping("/items")
    @PreAuthorize(PICK)
    @Operation(summary = "Sellable items for pickers", description = "Active items only, matched on name, SKU, barcode or brand. Empty query returns all.")
    public ResponseEntity<ApiResponse<List<CatalogueItemSummary>>> searchItems(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.success(catalogueService.searchItems(TenantContext.getTenantIdAsObject(), query)));
    }

    @GetMapping("/items/search")
    @PreAuthorize(READ)
    @Operation(summary = "Catalogue list", description = "Paged, filtered, sorted. Includes items in every status.")
    public ResponseEntity<ApiResponse<Page<CatalogueItemResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) CatalogueItemStatus status,
            @RequestParam(required = false) CatalogueItemType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "false") boolean desc,
            Authentication auth) {
        var out = catalogueService.listItems(TenantContext.getTenantIdAsObject(), new ItemFilter(q, categoryId, status, type), page, size, sort, desc, caller(auth));
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "One catalogue item")
    public ResponseEntity<ApiResponse<CatalogueItemResponse>> getItem(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(catalogueService.getItem(TenantContext.getTenantIdAsObject(), id, caller(auth))));
    }

    @GetMapping("/items/{id}/history")
    @PreAuthorize(READ)
    @Operation(summary = "Change history of an item")
    public ResponseEntity<ApiResponse<Page<CatalogueHistoryResponse>>> history(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(catalogueService.history(TenantContext.getTenantIdAsObject(), id, page, size)));
    }

    @PostMapping("/items")
    @PreAuthorize(WRITE)
    @Operation(summary = "Create a catalogue item")
    public ResponseEntity<ApiResponse<CatalogueItemResponse>> createItem(@Valid @RequestBody CreateItemRequest request, Authentication auth) {
        var item = catalogueService.createItem(TenantContext.getTenantIdAsObject(), request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item created", item));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Update a catalogue item")
    public ResponseEntity<ApiResponse<CatalogueItemResponse>> updateItem(
            @PathVariable UUID id, @Valid @RequestBody CreateItemRequest request, Authentication auth) {
        var item = catalogueService.updateItem(TenantContext.getTenantIdAsObject(), id, request, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Item updated", item));
    }

    @PostMapping("/items/{id}/status")
    @PreAuthorize(WRITE)
    @Operation(summary = "Move an item through its lifecycle", description = "Suspending, discontinuing and archiving need a reason; the last two need the archive permission.")
    public ResponseEntity<ApiResponse<CatalogueItemResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ItemStatusRequest request, Authentication auth) {
        var item = catalogueService.changeStatus(TenantContext.getTenantIdAsObject(), id, request, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Status changed", item));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize(ARCHIVE)
    @Operation(summary = "Soft delete a catalogue item")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable UUID id, Authentication auth) {
        catalogueService.softDeleteItem(TenantContext.getTenantIdAsObject(), id, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Item deleted", null));
    }

    // ── Categories ───────────────────────────────────────────────────────────

    @GetMapping("/categories")
    @PreAuthorize(PICK)
    @Operation(summary = "List all categories", description = "With the number of live items in each.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(catalogueService.getCategories(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/categories")
    @PreAuthorize(WRITE)
    @Operation(summary = "Create a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request, Authentication auth) {
        var category = catalogueService.createCategory(TenantContext.getTenantIdAsObject(), request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Category created", category));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Update a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody CreateCategoryRequest request, Authentication auth) {
        var category = catalogueService.updateCategory(TenantContext.getTenantIdAsObject(), id, request, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Category updated", category));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(ARCHIVE)
    @Operation(summary = "Delete an empty category", description = "Refused while the category still has items.")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id, Authentication auth) {
        catalogueService.deleteCategory(TenantContext.getTenantIdAsObject(), id, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    // ── Units of measure ─────────────────────────────────────────────────────

    @GetMapping("/units")
    @PreAuthorize(PICK)
    @Operation(summary = "Units of measure", description = "A tenant with none yet gets the standard set.")
    public ResponseEntity<ApiResponse<List<CatalogueUnitResponse>>> getUnits() {
        return ResponseEntity.ok(ApiResponse.success(structureService.listUnits(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/units")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Add a unit of measure")
    public ResponseEntity<ApiResponse<CatalogueUnitResponse>> createUnit(@Valid @RequestBody CatalogueUnitRequest request, Authentication auth) {
        var unit = structureService.createUnit(TenantContext.getTenantIdAsObject(), request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Unit created", unit));
    }

    @PutMapping("/units/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Edit a unit", description = "Name, kind, decimals and on/off. The code cannot change.")
    public ResponseEntity<ApiResponse<CatalogueUnitResponse>> updateUnit(
            @PathVariable UUID id, @Valid @RequestBody CatalogueUnitRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Unit updated", structureService.updateUnit(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Delete an unused unit", description = "Refused while items use it; switch it off instead.")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable UUID id, Authentication auth) {
        structureService.deleteUnit(TenantContext.getTenantIdAsObject(), id, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Unit deleted", null));
    }

    // ── Attribute definitions ────────────────────────────────────────────────

    @GetMapping("/attributes")
    @PreAuthorize(READ)
    @Operation(summary = "Attribute definitions", description = "All of them; an item gets those of its category, the categories above it, and the global ones.")
    public ResponseEntity<ApiResponse<List<CatalogueAttributeResponse>>> getAttributes() {
        return ResponseEntity.ok(ApiResponse.success(structureService.listDefinitions(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/attributes")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Add an attribute definition")
    public ResponseEntity<ApiResponse<CatalogueAttributeResponse>> createAttribute(@Valid @RequestBody CatalogueAttributeRequest request, Authentication auth) {
        var d = structureService.createDefinition(TenantContext.getTenantIdAsObject(), request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attribute created", d));
    }

    @PutMapping("/attributes/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Edit an attribute definition", description = "Code, type and category are fixed once created.")
    public ResponseEntity<ApiResponse<CatalogueAttributeResponse>> updateAttribute(
            @PathVariable UUID id, @Valid @RequestBody CatalogueAttributeRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Attribute updated", structureService.updateDefinition(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @DeleteMapping("/attributes/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Delete an attribute that has no values", description = "Refused while items hold a value; switch it off instead.")
    public ResponseEntity<ApiResponse<Void>> deleteAttribute(@PathVariable UUID id, Authentication auth) {
        structureService.deleteDefinition(TenantContext.getTenantIdAsObject(), id, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Attribute deleted", null));
    }
    // ── Pricing ──────────────────────────────────────────────────────────────

    @GetMapping("/price-lists")
    @PreAuthorize(READ)
    @Operation(summary = "Price lists", description = "The default (Retail) list is created the first time this is called.")
    public ResponseEntity<ApiResponse<List<CataloguePriceListResponse>>> getPriceLists() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.lists(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/price-lists")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Add a price list")
    public ResponseEntity<ApiResponse<CataloguePriceListResponse>> createPriceList(@Valid @RequestBody CataloguePriceListRequest request, Authentication auth) {
        var l = pricingService.createList(TenantContext.getTenantIdAsObject(), request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Price list created", l));
    }

    @PutMapping("/price-lists/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Edit a price list", description = "Name, order and on/off. The code cannot change.")
    public ResponseEntity<ApiResponse<CataloguePriceListResponse>> updatePriceList(
            @PathVariable UUID id, @Valid @RequestBody CataloguePriceListRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Price list updated", pricingService.updateList(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @DeleteMapping("/price-lists/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Delete a price list with no price history")
    public ResponseEntity<ApiResponse<Void>> deletePriceList(@PathVariable UUID id, Authentication auth) {
        pricingService.deleteList(TenantContext.getTenantIdAsObject(), id, caller(auth));
        return ResponseEntity.ok(ApiResponse.success("Price list deleted", null));
    }

    @GetMapping("/settings")
    @PreAuthorize(READ)
    @Operation(summary = "Catalogue settings of this tenant")
    public ResponseEntity<ApiResponse<CatalogueSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Turn price-change approval on or off for this tenant")
    public ResponseEntity<ApiResponse<CatalogueSettingsResponse>> updateSettings(@Valid @RequestBody CatalogueSettingsRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Settings saved",
                pricingService.setApprovalRequired(TenantContext.getTenantIdAsObject(), request.priceApprovalRequired(), caller(auth))));
    }

    @GetMapping("/items/{id}/pricing")
    @PreAuthorize(PRICE_READ)
    @Operation(summary = "Prices of one item on every list, with cost, margin and open changes")
    public ResponseEntity<ApiResponse<ItemPricingResponse>> itemPricing(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.pricing(TenantContext.getTenantIdAsObject(), id, caller(auth))));
    }

    @GetMapping("/items/{id}/price-changes")
    @PreAuthorize(PRICE_READ)
    @Operation(summary = "Price history of one item")
    public ResponseEntity<ApiResponse<List<PriceChangeResponse>>> priceHistory(@PathVariable UUID id, @RequestParam(defaultValue = "50") int size, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.itemHistory(TenantContext.getTenantIdAsObject(), id, size, caller(auth))));
    }

    @PostMapping("/items/{id}/price-changes")
    @PreAuthorize(PRICE_WRITE)
    @Operation(summary = "Propose a price change", description = "Waits for approval when the tenant requires it; otherwise applied or scheduled at once.")
    public ResponseEntity<ApiResponse<PriceChangeResponse>> proposePrice(@PathVariable UUID id, @Valid @RequestBody PriceChangeRequest request, Authentication auth) {
        var c = pricingService.propose(TenantContext.getTenantIdAsObject(), id, request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Price change recorded", c));
    }

    @PutMapping("/items/{id}/min-price")
    @PreAuthorize(PRICE_WRITE)
    @Operation(summary = "Set or clear the minimum price of an item")
    public ResponseEntity<ApiResponse<ItemPricingResponse>> minPrice(@PathVariable UUID id, @RequestBody MinPriceRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Minimum price saved", pricingService.setMinPrice(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @GetMapping("/price-changes")
    @PreAuthorize(PRICE_QUEUE)
    @Operation(summary = "Price changes by status", description = "PENDING is the approval queue, SCHEDULED the changes waiting for their date.")
    public ResponseEntity<ApiResponse<List<PriceChangeResponse>>> priceQueue(@RequestParam(defaultValue = "PENDING") PriceChangeStatus status, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.queue(TenantContext.getTenantIdAsObject(), status, caller(auth))));
    }

    @PostMapping("/price-changes/{id}/approve")
    @PreAuthorize(PRICE_DECIDE)
    @Operation(summary = "Approve a price change", description = "Never the person who proposed it.")
    public ResponseEntity<ApiResponse<PriceChangeResponse>> approvePrice(@PathVariable UUID id, @RequestBody(required = false) PriceDecisionRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Price change approved", pricingService.approve(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @PostMapping("/price-changes/{id}/reject")
    @PreAuthorize(PRICE_DECIDE)
    @Operation(summary = "Reject a price change", description = "A note is required. Never the person who proposed it.")
    public ResponseEntity<ApiResponse<PriceChangeResponse>> rejectPrice(@PathVariable UUID id, @RequestBody(required = false) PriceDecisionRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Price change rejected", pricingService.reject(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }

    @PostMapping("/price-changes/{id}/cancel")
    @PreAuthorize(PRICE_QUEUE)
    @Operation(summary = "Cancel an open price change", description = "The proposer, or an approver.")
    public ResponseEntity<ApiResponse<PriceChangeResponse>> cancelPrice(@PathVariable UUID id, @RequestBody(required = false) PriceDecisionRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Price change cancelled", pricingService.cancel(TenantContext.getTenantIdAsObject(), id, request, caller(auth))));
    }
    // ── Variants ─────────────────────────────────────────────────────────────

    @GetMapping("/items/{id}/variants")
    @PreAuthorize(READ)
    @Operation(summary = "Options and variants of an item")
    public ResponseEntity<ApiResponse<VariantsResponse>> variants(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(variantService.view(TenantContext.getTenantIdAsObject(), id, caller(auth))));
    }

    @PostMapping("/items/{id}/variants")
    @PreAuthorize(WRITE)
    @Operation(summary = "Add variants to an item", description = "Options with their values, and the combinations to create. A variant is an item of its own.")
    public ResponseEntity<ApiResponse<VariantsResponse>> createVariants(@PathVariable UUID id, @RequestBody CreateVariantsRequest request, Authentication auth) {
        var out = variantService.create(TenantContext.getTenantIdAsObject(), id, request, caller(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Variants created", out));
    }

    @PutMapping("/items/{id}/variants/{variantId}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Edit a variant's SKU, barcode, price and cost")
    public ResponseEntity<ApiResponse<CatalogueItemResponse>> updateVariant(
            @PathVariable UUID id, @PathVariable UUID variantId, @RequestBody UpdateVariantRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Variant updated", variantService.update(TenantContext.getTenantIdAsObject(), id, variantId, request, caller(auth))));
    }
}
