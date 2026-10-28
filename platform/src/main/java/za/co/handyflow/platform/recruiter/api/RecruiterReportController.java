package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterReportService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterReportRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterReportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recruiter/reports")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Recruiting reports")
public class RecruiterReportController {

    private final RecruiterReportService reports;

    private static LocalDate today() { return LocalDate.now(RecruiterReportRules.ZONE); }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITER_REPORTS')")
    @Operation(summary = "The reports and how each figure is defined")
    public ResponseEntity<ApiResponse<List<ReportInfo>>> catalogue() {
        return ResponseEntity.ok(ApiResponse.success(reports.catalogue()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_REPORTS')")
    @Operation(summary = "Run a report for a date range (default: the last 90 days)")
    public ResponseEntity<ApiResponse<ReportTable>> run(@PathVariable String id,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reports.run(TenantContext.getTenantIdAsObject(), id, from, to, today())));
    }

    @GetMapping("/{id}/export.csv")
    @PreAuthorize("hasAuthority('RECRUITER_REPORTS')")
    @Operation(summary = "The same report as a CSV file")
    public ResponseEntity<byte[]> export(@PathVariable String id,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportTable t = reports.run(TenantContext.getTenantIdAsObject(), id, from, to, today());
        String csv = "﻿" + RecruiterReportRules.csv(t.columns(), t.rows());
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=recruiter-" + t.id() + ".csv")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
