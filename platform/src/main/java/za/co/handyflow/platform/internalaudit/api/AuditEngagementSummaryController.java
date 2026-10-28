package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditEngagementSummaryService;
import za.co.handyflow.platform.internalaudit.application.internal.EngagementSummaryBuilder.Summary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit/engagements/{id}")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Engagement summary", description = "How far an engagement has got, by phase, with its milestones")
public class AuditEngagementSummaryController {
    private final AuditEngagementSummaryService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Progress by phase (planning, fieldwork, testing, reporting), milestones, and counts of workpapers, findings and actions")
    public ResponseEntity<ApiResponse<Summary>> get(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject(), id, LocalDate.now(ZoneId.of("Africa/Johannesburg")))));
    }

    @GetMapping("/workpapers")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Every current workpaper of the engagement in one call (no file contents)")
    public ResponseEntity<ApiResponse<java.util.List<za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse>>> workpapers(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.workpapers(TenantContext.getTenantIdAsObject(), id)));
    }
}
