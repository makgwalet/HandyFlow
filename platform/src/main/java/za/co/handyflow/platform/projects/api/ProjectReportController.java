package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectReportExporter;
import za.co.handyflow.platform.projects.application.internal.ProjectReportService;
import za.co.handyflow.platform.projects.dto.ReportResponse;
import za.co.handyflow.platform.projects.dto.RiskHeatmapResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project reports", description = "Portfolio delivery and profitability, and the risk heat map")
public class ProjectReportController {

    private final ProjectReportService reports;
    private final ProjectReportExporter exporter;

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Portfolio report for a period (default: this year to date). format=json (default) | csv | pdf")
    public ResponseEntity<?> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "json") String format) {
        ReportResponse r = reports.report(TenantContext.getTenantIdAsObject(), from, to);
        String name = "projects-report-" + r.from() + "-to-" + r.to();
        return switch (format.toLowerCase()) {
            case "csv" -> file(exporter.csv(r), name + ".csv", "text/csv;charset=UTF-8");
            case "pdf" -> file(exporter.pdf(r), name + ".pdf", MediaType.APPLICATION_PDF_VALUE);
            case "json" -> ResponseEntity.ok(ApiResponse.success("Success", r));
            default -> throw new IllegalArgumentException("format must be json, csv or pdf");
        };
    }

    @GetMapping("/risk-heatmap")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Open risks by probability × impact across the projects you can see")
    public ResponseEntity<ApiResponse<RiskHeatmapResponse>> portfolioHeatmap() {
        return ResponseEntity.ok(ApiResponse.success("Success", reports.heatmap(TenantContext.getTenantIdAsObject(), null)));
    }

    @GetMapping("/{projectId}/risks/heatmap")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Open risks on one project by probability × impact")
    public ResponseEntity<ApiResponse<RiskHeatmapResponse>> projectHeatmap(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success", reports.heatmap(TenantContext.getTenantIdAsObject(), projectId)));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType(type)).body(body);
    }
}
