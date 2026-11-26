package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAuditService;
import za.co.handyflow.platform.trainingprovider.dto.AuditDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/quality-audits")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Quality audits", description = "Internal audits against the provider's own checklists, with corrective actions")
public class TrainProvAuditController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvAuditService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/templates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<TemplateView>>> templates() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.templates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/templates")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> createTemplate(@Valid @RequestBody TemplateRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Checklist saved", service.createTemplate(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/templates/{id}/versions")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> newVersion(@PathVariable UUID id, @Valid @RequestBody VersionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("New version saved", service.newVersion(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/templates/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> retire(@PathVariable UUID id, @Valid @RequestBody RetireRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Checklist retired", service.retireTemplate(TenantContext.getTenantIdAsObject(), id, r.reason())));
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AuditSummary>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String scope) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, scope)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AuditDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> create(@Valid @RequestBody AuditRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Audit planned", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> start(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Audit started", service.start(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/items/{itemId}/answer")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> answer(@PathVariable UUID itemId, @Valid @RequestBody AnswerRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.answer(TenantContext.getTenantIdAsObject(), itemId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> addAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action added", service.addAction(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/actions/{actionId}/done")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> done(@PathVariable UUID actionId, @Valid @RequestBody DoneRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action marked done", service.markDone(TenantContext.getTenantIdAsObject(), actionId, r.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/actions/{actionId}/verify")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> verify(@PathVariable UUID actionId, @Valid @RequestBody VerifyRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action verified", service.verify(TenantContext.getTenantIdAsObject(), actionId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/actions/{actionId}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> cancelAction(@PathVariable UUID actionId, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action cancelled", service.cancelAction(TenantContext.getTenantIdAsObject(), actionId, r.reason(), today())));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Audit completed", service.complete(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuditDetail>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Audit cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, r.reason(), today())));
    }
}
