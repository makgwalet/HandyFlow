package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CrmAuditService;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.dto.AuditEventResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/audit")
@RequiredArgsConstructor
@Tag(name = "CRM - Audit log", description = "Who changed what in the CRM")
public class CrmAuditController {
    private final CrmAuditService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Search the audit log, newest first (page size default 50, at most 100)")
    public ResponseEntity<ApiResponse<Page<AuditEventResponse>>> search(
            @RequestParam(required = false) AuditEntity entityType, @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(service.search(TenantContext.getTenantIdAsObject(), entityType, actorId, from, to, page, size)));
    }
}
