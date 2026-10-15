package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.AuditPackService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The financial audit pack. Holds the whole ledger for a period, so it needs both accounting and audit-administration rights. */
@RestController
@RequestMapping("/api/v1/accounting/audit-pack")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ACCOUNTING_READ') and hasAuthority('AUDIT_ADMIN')")
@Tag(name = "Audit pack", description = "Reproducible financial schedules for an external audit")
public class AuditPackController {

    private final AuditPackService service;

    public record PackRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, UUID engagementId) {}

    @PostMapping(produces = "application/zip")
    @Operation(summary = "Build the pack for a period and download it as a ZIP")
    public ResponseEntity<byte[]> generate(@RequestBody PackRequest req) {
        var g = service.generate(TenantContext.getTenantIdAsObject(), req.from(), req.to(), req.engagementId(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        String name = "audit-pack-" + req.from() + "-to-" + req.to() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header("X-Audit-Pack-Run", g.run().id().toString())
                .header("X-Audit-Pack-Sha256", g.pack().packSha256())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Audit-Pack-Run, X-Audit-Pack-Sha256, Content-Disposition")
                .contentType(MediaType.parseMediaType("application/zip")).body(g.pack().zip());
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<AuditPackService.RunInfo>>> runs() {
        return ResponseEntity.ok(ApiResponse.success(service.runs(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping(value = "/runs/{id}/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> manifest(@PathVariable UUID id) {
        return ResponseEntity.ok(service.manifest(TenantContext.getTenantIdAsObject(), id));
    }

    @PostMapping("/runs/{id}/verify")
    @Operation(summary = "Rebuild the pack for the same period and say whether it still matches")
    public ResponseEntity<ApiResponse<AuditPackService.Verification>> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.verify(TenantContext.getTenantIdAsObject(), id)));
    }
}
