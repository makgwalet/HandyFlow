// security/api/ReportCatalogueController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.ReportRunService;
import za.co.handyflow.platform.security.dto.ReportCatalogueDtos.Catalogue;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/security/reports")
@RequiredArgsConstructor
@Tag(name = "Security - Reports")
public class ReportCatalogueController {

    private final ReportRunService runService;
    private final FeatureGuard featureGuard;

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The reports on offer, each with when it was last generated and by whom, plus recent runs")
    public ResponseEntity<ApiResponse<Catalogue>> catalogue() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(runService.catalogue(TenantContext.getTenantIdAsObject())));
    }
}
