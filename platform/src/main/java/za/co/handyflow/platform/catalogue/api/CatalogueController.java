package za.co.handyflow.platform.catalogue.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService;
import za.co.handyflow.platform.catalogue.dto.CreateCategoryRequest;
import za.co.handyflow.platform.catalogue.dto.CreateItemRequest;
import za.co.handyflow.platform.catalogue.dto.CategoryResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
@Tag(name = "Product Catalogue", description = "Manage your product and service catalogue")
public class CatalogueController {

    private final CatalogueService catalogueService;

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Search catalogue items", description = "Search by name. Leave query empty to get all items.")
    public ResponseEntity<ApiResponse<List<CatalogueItemSummary>>> searchItems(
            @RequestParam(required = false) String query
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var items = catalogueService.searchItems(tenantId, query);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Create a catalogue item")
    public ResponseEntity<ApiResponse<CatalogueItemSummary>> createItem(
            @Valid @RequestBody CreateItemRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var item = catalogueService.createItem(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Item created", item));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Soft delete a catalogue item")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        catalogueService.softDeleteItem(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted", null));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "List all categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        var tenantId = TenantContext.getTenantIdAsObject();
        var categories = catalogueService.getCategories(tenantId);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Create a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var category = catalogueService.createCategory(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", category));
    }
}
