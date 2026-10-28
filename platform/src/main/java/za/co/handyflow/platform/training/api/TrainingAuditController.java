package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingAuditService;
import za.co.handyflow.platform.training.domain.model.TrainingAuditRules;
import za.co.handyflow.platform.training.dto.TrainingAuditDtos.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training/audit")
@RequiredArgsConstructor
@Tag(name = "Training", description = "Audit trail")
public class TrainingAuditController {

    private final TrainingAuditService audit;
    private final FeatureGuard featureGuard;

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('TRAINING_AUDIT')")
    @Operation(summary = "The actions that appear in the audit trail")
    public ResponseEntity<ApiResponse<List<String>>> actions() {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(TrainingAuditRules.actions().stream().sorted().toList()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRAINING_AUDIT')")
    @Operation(summary = "The audit trail, newest first, for a date range (default: the last 90 days)")
    public ResponseEntity<ApiResponse<AuditPage>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") int page) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(audit.page(TenantContext.getTenantIdAsObject(), from, to, action, entityId, page)));
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAuthority('TRAINING_AUDIT')")
    @Operation(summary = "The same trail as a CSV file. The export is itself recorded")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID entityId) {
        featureGuard.requireModule("training");
        List<AuditEntry> rows = audit.forExport(TenantContext.getTenantIdAsObject(), from, to, action, entityId);
        String csv = "﻿" + TrainingAuditRules.csv(
                List.of("When (UTC)", "Who", "Action", "Record", "Record id", "Detail"),
                rows.stream().map(r -> List.<Object>of(String.valueOf(r.occurredAt()), r.actorName() == null ? "" : r.actorName(), r.action(), r.entityType(),
                        r.entityId() == null ? "" : r.entityId().toString(), r.detail() == null ? "" : r.detail())).toList());
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=training-audit.csv")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
