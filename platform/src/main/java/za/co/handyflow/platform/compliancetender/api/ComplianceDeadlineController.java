package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.ComplianceDeadlineService;
import za.co.handyflow.platform.compliancetender.dto.ComplianceDeadlineResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceDeadlineRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/deadlines")
@RequiredArgsConstructor
@Tag(name = "Compliance - Deadlines", description = "The compliance calendar — renewals, annual returns, declarations")
public class ComplianceDeadlineController {

    private final ComplianceDeadlineService deadlineService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Every pending deadline, soonest first — the compliance calendar view")
    public ResponseEntity<ApiResponse<List<ComplianceDeadlineResponse>>> getPendingDeadlines() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                deadlineService.getPendingDeadlines(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceDeadlineResponse>> create(
            @Valid @RequestBody CreateComplianceDeadlineRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Deadline created",
                deadlineService.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/mark-done")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceDeadlineResponse>> markDone(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Deadline marked done",
                deadlineService.markDone(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        deadlineService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Deadline deleted", null));
    }
}
