package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/** Standard reports: view on screen, download as CSV (audited) */
@RestController
@RequestMapping("/api/v1/legalcompliance/reports")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Reports", description = "Standard reports: view on screen, download as CSV (audited)")
public class ReportController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_EXPORT = "hasAnyAuthority('LEGALCOMPLIANCE_REPORT_EXPORT','LEGALCOMPLIANCE_ADMIN')";

    private final ReportService reports;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ReportInfo>>> catalogue() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(reports.catalogue()));
    }

    @GetMapping("/{key}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ReportResult>> run(@PathVariable String key, @RequestParam(required = false) String asOf) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(reports.run(tenant(), key, asOf, TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{key}/export")
    @PreAuthorize(CAN_EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable String key, @RequestParam(required = false) String asOf) {
        featureGuard.requireModule("legalcompliance");
        ReportService.ExportFile f = reports.exportCsv(tenant(), key, asOf, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"" + f.fileName() + "\"")
                .header("Cache-Control", "no-store")
                .body(f.bytes());
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
