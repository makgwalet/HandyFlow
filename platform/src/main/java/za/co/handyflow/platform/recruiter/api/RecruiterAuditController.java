package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterAuditService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterAuditRules;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterReportRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterAuditDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter/audit")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Audit trail")
public class RecruiterAuditController {

    private final RecruiterAuditService audit;

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('RECRUITER_AUDIT')")
    @Operation(summary = "The actions that appear in the audit trail")
    public ResponseEntity<ApiResponse<List<String>>> actions() {
        return ResponseEntity.ok(ApiResponse.success(RecruiterAuditRules.actions().stream().sorted().toList()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITER_AUDIT')")
    @Operation(summary = "The audit trail, newest first, for a date range (default: the last 90 days)")
    public ResponseEntity<ApiResponse<AuditPage>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.success(audit.page(TenantContext.getTenantIdAsObject(), from, to, action, applicationId, page)));
    }

    @GetMapping("/cv-access")
    @PreAuthorize("hasAuthority('RECRUITER_AUDIT')")
    @Operation(summary = "Who has opened candidates' CVs, with anyone who opened an unusual number in an hour flagged")
    public ResponseEntity<ApiResponse<List<CvAccessRow>>> cvAccess(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success(audit.cvAccess(TenantContext.getTenantIdAsObject(), days)));
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAuthority('RECRUITER_AUDIT')")
    @Operation(summary = "The same trail as a CSV file. The export is itself recorded")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID applicationId) {
        List<AuditEntry> rows = audit.forExport(TenantContext.getTenantIdAsObject(), from, to, action, applicationId);
        String csv = "﻿" + RecruiterReportRules.csv(
                List.of("When (UTC)", "Who", "Action", "Record", "Record id", "Application id", "Detail"),
                rows.stream().map(r -> List.<Object>of(String.valueOf(r.occurredAt()), r.actorName() == null ? "" : r.actorName(), r.action(), r.entityType(),
                        r.entityId() == null ? "" : r.entityId().toString(), r.applicationId() == null ? "" : r.applicationId().toString(), r.detail() == null ? "" : r.detail())).toList());
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=recruiter-audit.csv")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
