package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.RecoveryReportService;
import za.co.handyflow.platform.debtcollection.application.internal.RecoveryReportService.RecoveryReport;
import za.co.handyflow.platform.debtcollection.application.internal.RecoveryReportService.TrendPoint;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;

/** Recovery figures from the ledger and the exposure trend from daily snapshots (both start at deployment). */
@RestController
@RequestMapping("/api/v1/debtcollection/dashboard")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Recovery Reports", description = "Recovered cash and exposure trend")
public class RecoveryReportController {

    private final RecoveryReportService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/recovery")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Net recovered (payments − reversals) by month, source and collector; fees reported separately. Default month-to-date")
    public ResponseEntity<ApiResponse<RecoveryReport>> recovery(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(service.recovery(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Outstanding vs recovered per snapshot day (default last 90 days)")
    public ResponseEntity<ApiResponse<List<TrendPoint>>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(service.trend(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
