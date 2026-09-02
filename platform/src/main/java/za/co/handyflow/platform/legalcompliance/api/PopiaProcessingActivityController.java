package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.PopiaProcessingActivityService;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;
import za.co.handyflow.platform.legalcompliance.dto.CreatePopiaActivityRequest;
import za.co.handyflow.platform.legalcompliance.dto.PopiaProcessingActivityResponse;
import za.co.handyflow.platform.legalcompliance.dto.UpdatePopiaActivityRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Org-wide POPIA processing-activity register — see PopiaProcessingActivity's own class Javadoc for scope vs. crm.CustomerConsent. */
@RestController
@RequestMapping("/api/v1/legalcompliance/popia-activities")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - POPIA Register", description = "Org-wide POPIA processing-activity register")
public class PopiaProcessingActivityController {

    private final PopiaProcessingActivityService activityService;
    private final LegalCompliancePdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<PopiaProcessingActivityResponse>>> list() {
        featureGuard.requireModule("legalcompliance");
        List<PopiaProcessingActivityResponse> result = activityService
                .list(TenantContext.getTenantIdAsObject()).stream()
                .map(PopiaProcessingActivityResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<PopiaProcessingActivityResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        PopiaProcessingActivity activity = activityService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(PopiaProcessingActivityResponse.of(activity)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Register a processing activity")
    public ResponseEntity<ApiResponse<PopiaProcessingActivityResponse>> create(
            @Valid @RequestBody CreatePopiaActivityRequest req) {
        featureGuard.requireModule("legalcompliance");
        PopiaProcessingActivity activity = activityService.create(
                TenantContext.getTenantIdAsObject(), req.activityName(), req.dataCategory(), req.purpose(),
                req.lawfulBasis(), req.responsibleDepartment(), req.responsibleUserId(), req.responsibleUserName(),
                req.retentionPeriodDescription(), req.crossBorderTransfer(), req.crossBorderDetails(),
                req.securityMeasures(), req.reviewDate(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Processing activity registered", PopiaProcessingActivityResponse.of(activity)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<PopiaProcessingActivityResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePopiaActivityRequest req) {
        featureGuard.requireModule("legalcompliance");
        PopiaProcessingActivity activity = activityService.update(
                TenantContext.getTenantIdAsObject(), id, req.activityName(), req.dataCategory(), req.purpose(),
                req.lawfulBasis(), req.responsibleDepartment(), req.responsibleUserId(), req.responsibleUserName(),
                req.retentionPeriodDescription(), req.crossBorderTransfer(), req.crossBorderDetails(),
                req.securityMeasures(), req.reviewDate());
        return ResponseEntity.ok(ApiResponse.success("Processing activity updated", PopiaProcessingActivityResponse.of(activity)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<PopiaProcessingActivityResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        PopiaProcessingActivity activity = activityService.deactivate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Deactivated", PopiaProcessingActivityResponse.of(activity)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<PopiaProcessingActivityResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        PopiaProcessingActivity activity = activityService.reactivate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Reactivated", PopiaProcessingActivityResponse.of(activity)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        activityService.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Processing activity deleted", null));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Export the full POPIA processing-activity register as a PDF")
    public ResponseEntity<byte[]> exportPdf() {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = pdfService.generatePopiaRegister(null, activityService.list(tenantId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"popia-processing-activity-register.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
