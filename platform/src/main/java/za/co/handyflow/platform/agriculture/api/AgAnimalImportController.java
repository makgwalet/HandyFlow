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
import za.co.handyflow.platform.agriculture.application.internal.AgAnimalImportService;
import za.co.handyflow.platform.agriculture.dto.AnimalImportRequest;
import za.co.handyflow.platform.agriculture.dto.AnimalImportResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/farms")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Animal import", description = "Bring a herd in from a spreadsheet")
public class AgAnimalImportController {

    private final AgAnimalImportService importService;
    private final FeatureGuard featureGuard;

    @PostMapping("/{farmId}/animals/import")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Import animals from CSV rows (500 at most). Set dryRun to check without creating anything",
            description = "Every row is checked on the server. A tag already on the farm, or repeated in the file, is skipped and reported, never overwritten. " +
                    "Rows with a problem are reported with the reason; the others are still created in one transaction.")
    public ResponseEntity<ApiResponse<AnimalImportResponse>> importAnimals(@PathVariable UUID farmId, @Valid @RequestBody AnimalImportRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(importService.importAnimals(TenantContext.getTenantIdAsObject(), farmId, request)));
    }
}
