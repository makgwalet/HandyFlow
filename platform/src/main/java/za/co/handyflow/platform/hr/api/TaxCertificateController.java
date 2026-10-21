package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.TaxCertificateService;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Tax certificates (IRP5 / IT3(a)) and the EMP501 reconciliation workpaper. */
@RestController
@RequestMapping("/api/v1/hr/tax-certificates")
@RequiredArgsConstructor
@Tag(name = "Tax certificates", description = "IRP5 / IT3(a) and the reconciliation with the EMP201 returns")
public class TaxCertificateController {

    private static final String EXPORT = "T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canExport(authentication)";
    private static final String CONFIGURE = "T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canConfigure(authentication)";

    private final TaxCertificateService service;

    @GetMapping("/settings")
    @PreAuthorize(EXPORT)
    @Operation(summary = "The employer's tax reference numbers and retirement fund type")
    public ResponseEntity<ApiResponse<Settings>> settings() {
        return ResponseEntity.ok(ApiResponse.success(service.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize(CONFIGURE)
    public ResponseEntity<ApiResponse<Settings>> saveSettings(@RequestBody Settings req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Tax settings saved",
                service.saveSettings(TenantContext.getTenantIdAsObject(), req, user(principal))));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize(EXPORT)
    @Operation(summary = "What the certificates would be for a reconciliation, the checks on them, and the comparison with the EMP201 returns. Issues nothing")
    public ResponseEntity<ApiResponse<Reconciliation>> reconciliation(@RequestParam int taxYear, @RequestParam String period) {
        return ResponseEntity.ok(ApiResponse.success(service.reconciliation(TenantContext.getTenantIdAsObject(), taxYear, period.toUpperCase())));
    }

    @GetMapping("/issued")
    @PreAuthorize(EXPORT)
    public ResponseEntity<ApiResponse<List<Certificate>>> issued(@RequestParam int taxYear, @RequestParam String period) {
        return ResponseEntity.ok(ApiResponse.success(service.issuedCertificates(TenantContext.getTenantIdAsObject(), taxYear, period.toUpperCase())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(EXPORT)
    public ResponseEntity<ApiResponse<Certificate>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAuthority('PAYROLL_CERTIFICATE_ISSUE')")
    @Operation(summary = "Issue the certificates of a reconciliation. Refused while any run, EMP201, employer or employee check fails")
    public ResponseEntity<ApiResponse<IssueResult>> issue(@RequestBody IssueRequest req, Principal principal) {
        if (req.taxYear() == null || req.period() == null) throw new IllegalArgumentException("Choose the tax year and the period.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Certificates issued",
                service.issue(TenantContext.getTenantIdAsObject(), req.taxYear(), req.period().toUpperCase(), req.reason(), user(principal))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYROLL_CERTIFICATE_ISSUE')")
    public ResponseEntity<ApiResponse<Certificate>> cancel(@PathVariable UUID id, @RequestBody ReasonRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Certificate cancelled",
                service.cancel(TenantContext.getTenantIdAsObject(), id, req.reason(), user(principal))));
    }

    @PostMapping("/release")
    @PreAuthorize("hasAuthority('PAYROLL_CERTIFICATE_ISSUE')")
    @Operation(summary = "Let employees see their annual certificates. Interim certificates are for SARS only and cannot be released")
    public ResponseEntity<ApiResponse<Integer>> release(@RequestBody ReleaseRequest req, Principal principal) {
        if (req.taxYear() == null) throw new IllegalArgumentException("Choose the tax year.");
        return ResponseEntity.ok(ApiResponse.success("Certificates released",
                service.release(TenantContext.getTenantIdAsObject(), req.taxYear(), user(principal))));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize(EXPORT)
    @Operation(summary = "Issued certificates as a spreadsheet, one row per code. A workpaper, not a SARS import file")
    public ResponseEntity<byte[]> export(@RequestParam int taxYear, @RequestParam String period) {
        String p = period.toUpperCase();
        byte[] body = service.csv(TenantContext.getTenantIdAsObject(), taxYear, p).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"tax-certificates-" + taxYear + "-" + p.toLowerCase() + ".csv\"")
                .body(body);
    }

    private UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
