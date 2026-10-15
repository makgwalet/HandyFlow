package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.ExternalAuditDashboardBuilder.Dashboard;
import za.co.handyflow.platform.auditor.application.internal.ExternalAuditDashboardService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditors/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
@Tag(name = "External audit dashboard", description = "Open requests, overdue responses, open queries and unresolved review notes")
public class ExternalAuditDashboardController {
    private final ExternalAuditDashboardService service;

    @GetMapping
    @Operation(summary = "External audit dashboard, optionally for one engagement")
    public ResponseEntity<ApiResponse<Dashboard>> get(@RequestParam(required = false) UUID engagementId) {
        return ResponseEntity.ok(ApiResponse.success(service.build(TenantContext.getTenantIdAsObject(), engagementId, LocalDate.now())));
    }
}
