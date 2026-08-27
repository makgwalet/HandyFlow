// security/api/ScanLogController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.ScanLogService;
import za.co.handyflow.platform.security.dto.ScanLogResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/security/shifts")
@RequiredArgsConstructor
@Tag(name = "Security - Scans", description = "Checkpoint scan log queries")
public class ScanLogController {

    private final ScanLogService scanLogService;
    private final FeatureGuard   featureGuard;

    /**
     * Returns all checkpoint scans for a shift, ordered oldest-first.
     * LiveMapTab calls this on each active shift and reads the last element
     * to display "Last checkpoint: North Gate · 14:32".
     */
    @GetMapping("/{id}/scans")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all checkpoint scans for a shift — ordered oldest first")
    public ResponseEntity<ApiResponse<List<ScanLogResponse>>> getScans(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Success",
                scanLogService.getScansForShift(TenantContext.getTenantIdAsObject(), id)));
    }
}