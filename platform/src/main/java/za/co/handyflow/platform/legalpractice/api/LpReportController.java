package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpReportService;
import za.co.handyflow.platform.legalpractice.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Firm reports. Fees, debtors and trust balances are financial, so one authority covers them all. Opening a report is logged. */
@Slf4j
@RestController("legalPracticeReportController")
@RequestMapping("/api/v1/legal-practice/reports")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Reports", description = "Matters, unbilled work, debtors, billing and trust reports")
@PreAuthorize("hasAnyAuthority('LEGALPRACTICE_REPORT_VIEW','LEGALPRACTICE_ADMIN')")
public class LpReportController {

    private final LpReportService service;
    private final FeatureGuard featureGuard;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private TenantId open(String report) {
        featureGuard.requireModule("legalpractice");
        log.info("Legal practice report={} tenant={} user={}", report, tenant(), TenantContext.getCurrentUserId());
        return tenant();
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "-" + LocalDate.now(LpReportService.ZONE) + ".csv\"")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/matters")
    public ResponseEntity<ApiResponse<MattersReport>> matters() { return ResponseEntity.ok(ApiResponse.success(service.matters(open("matters")))); }

    @GetMapping("/matters.csv")
    public ResponseEntity<byte[]> mattersCsv() { return csv("matters", service.mattersCsv(open("matters.csv"))); }

    @GetMapping("/unbilled")
    public ResponseEntity<ApiResponse<UnbilledReport>> unbilled() { return ResponseEntity.ok(ApiResponse.success(service.unbilled(open("unbilled")))); }

    @GetMapping("/unbilled.csv")
    public ResponseEntity<byte[]> unbilledCsv() { return csv("unbilled-work", service.unbilledCsv(open("unbilled.csv"))); }

    @GetMapping("/debtors")
    public ResponseEntity<ApiResponse<DebtorsReport>> debtors() { return ResponseEntity.ok(ApiResponse.success(service.debtors(open("debtors")))); }

    @GetMapping("/debtors.csv")
    public ResponseEntity<byte[]> debtorsCsv() { return csv("debtors", service.debtorsCsv(open("debtors.csv"))); }

    @GetMapping("/billing")
    public ResponseEntity<ApiResponse<BillingReport>> billing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(service.billing(open("billing"), from, to)));
    }

    @GetMapping("/billing.csv")
    public ResponseEntity<byte[]> billingCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv("billing", service.billingCsv(open("billing.csv"), from, to));
    }

    @GetMapping("/trust")
    public ResponseEntity<ApiResponse<TrustReport>> trust(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asAt) {
        return ResponseEntity.ok(ApiResponse.success(service.trust(open("trust"), asAt)));
    }

    @GetMapping("/trust.csv")
    public ResponseEntity<byte[]> trustCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asAt) {
        return csv("trust-balances", service.trustCsv(open("trust.csv"), asAt));
    }
}
