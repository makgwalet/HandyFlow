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

    private final CatalogueService catalogueService;

    /** What the signed-in user may do, from their authorities. */
    private static Caller caller(Authentication auth) {
        Set<String> a = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = a.contains("SETTINGS_MANAGE");
        return new Caller(currentUserOrNull(),
                a.contains("CATALOGUE_PRICE_READ"),
                a.contains("CATALOGUE_PRICE_WRITE"),
                admin || a.contains("CATALOGUE_ARCHIVE"));
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
}
