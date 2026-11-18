package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractReportService;
import za.co.handyflow.platform.contracting.dto.ContractReportDtos.PortfolioReport;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Contract portfolio reporting. The module entitlement check covers /api/v1/contracts (ModuleEntitlementPaths). */
@RestController
@RequestMapping("/api/v1/contracts/reports")
@RequiredArgsConstructor
@Tag(name = "Contract reports")
public class ContractReportController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractReportService reports;

    @GetMapping("/portfolio")
    @PreAuthorize(READ)
    @Operation(summary = "Signing turnaround, value by type, expiry profile, obligation performance, renewal decisions and template legal currency")
    public ResponseEntity<ApiResponse<PortfolioReport>> portfolio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio report", reports.portfolio(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping(value = "/register.csv", produces = "text/csv")
    @PreAuthorize(READ)
    @Operation(summary = "Every contract as a CSV (up to 10,000, newest first)")
    public ResponseEntity<byte[]> register() {
        byte[] body = reports.registerCsv(TenantContext.getTenantIdAsObject()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract-register.csv\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
