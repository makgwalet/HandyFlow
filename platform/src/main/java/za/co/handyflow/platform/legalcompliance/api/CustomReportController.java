package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportService;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService;
import za.co.handyflow.platform.legalcompliance.dto.CustomReportDtos.SaveRequest;
import za.co.handyflow.platform.legalcompliance.dto.CustomReportDtos.View;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Custom reports: saved views over the standard reports. Anyone who can read reports can make and run their own; sharing needs a manager; downloading needs REPORT_EXPORT. */
@RestController
@RequestMapping("/api/v1/legalcompliance/custom-reports")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Custom reports", description = "Saved views over the standard reports")
public class CustomReportController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_EXPORT = "hasAnyAuthority('LEGALCOMPLIANCE_REPORT_EXPORT','LEGALCOMPLIANCE_ADMIN')";

    private final CustomReportService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<View>>> list() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), uid(), LCReportController.audience(), admin())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.get(tenant(), id, uid(), LCReportController.audience(), admin())));
    }

    @PostMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<View>> create(@Valid @RequestBody SaveRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Custom report saved", service.create(tenant(), req, uid(), uname(), LCReportController.audience(), canShare())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<View>> update(@PathVariable UUID id, @Valid @RequestBody SaveRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Custom report saved", service.update(tenant(), id, req, uid(), uname(), LCReportController.audience(), canShare(), admin())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        service.delete(tenant(), id, uid(), uname(), admin());
        return ResponseEntity.ok(ApiResponse.success("Custom report deleted", null));
    }

    @GetMapping("/{id}/run")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ReportResult>> run(@PathVariable UUID id, @RequestParam(required = false) String asOf) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.run(tenant(), id, asOf, uid(), uname(), LCReportController.audience())));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize(CAN_EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @RequestParam(required = false) String asOf, @RequestParam(required = false) String format) {
        featureGuard.requireModule("legalcompliance");
        ReportService.ExportFile f = service.export(tenant(), id, asOf, uid(), uname(), LCReportController.audience(), ReportService.Format.parse(format));
        return ResponseEntity.ok()
                .header("Content-Type", f.contentType())
                .header("Content-Disposition", "attachment; filename=\"" + f.fileName() + "\"")
                .header("Cache-Control", "no-store")
                .body(f.bytes());
    }

    private static boolean has(String... names) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> java.util.Arrays.asList(names).contains(a.getAuthority()));
    }
    private static boolean admin() { return has("LEGALCOMPLIANCE_ADMIN"); }
    private static boolean canShare() { return has("LEGALCOMPLIANCE_ADMIN", "LEGALCOMPLIANCE_MANAGE"); }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
