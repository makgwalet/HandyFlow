// security/api/CheckpointScanController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CheckpointScanService;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/security/checkpoints")
@RequiredArgsConstructor
@Tag(name = "Security - Checkpoints", description = "QR checkpoint scanning")
public class CheckpointScanController {

    private final CheckpointScanService scanService;
    private final FeatureGuard          featureGuard;

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Guard scans a QR checkpoint — logs timestamp and optional GPS")
    public ResponseEntity<ApiResponse<ScanResponse>> scan(
            @Valid @RequestBody ScanRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        var result = scanService.scan(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Checkpoint scanned", result));
    }
}