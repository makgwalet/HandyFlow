package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgStockImportService;
import za.co.handyflow.platform.agriculture.dto.StockImportRequest;
import za.co.handyflow.platform.agriculture.dto.StockImportResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/farms")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Stock import", description = "Bring stock items in from a spreadsheet")
public class AgStockImportController {

    private final AgStockImportService importService;
    private final FeatureGuard featureGuard;

    @PostMapping("/{farmId}/inventory-items/import")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Import stock items from CSV rows (500 at most). Set dryRun to check without creating anything",
            description = "Every row is checked on the server. An item name already on the farm, or repeated in the file, is skipped and reported, never changed. " +
                    "An opening quantity is posted as a RECEIPT. Rows with a problem are reported with the reason; the others are created in one transaction.")
    public ResponseEntity<ApiResponse<StockImportResponse>> importItems(@PathVariable UUID farmId, @Valid @RequestBody StockImportRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(importService.importItems(TenantContext.getTenantIdAsObject(), farmId, request)));
    }
}
