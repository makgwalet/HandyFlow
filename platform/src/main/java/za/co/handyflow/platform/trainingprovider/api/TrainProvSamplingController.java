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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvSamplingService;
import za.co.handyflow.platform.trainingprovider.dto.SamplingDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/moderation-sampling")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Moderation sampling", description = "The provider's own moderation sampling rules and draws")
public class TrainProvSamplingController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvSamplingService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/rules")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<RuleView>>> rules() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listRules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/rules")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> createRule(@Valid @RequestBody RuleRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Rule saved", service.createRule(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/rules/{id}/versions")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> addVersion(@PathVariable UUID id, @Valid @RequestBody SettingsRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("New version saved", service.addVersion(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/rules/{id}/name")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.rename(TenantContext.getTenantIdAsObject(), id, r.name())));
    }

    @PostMapping("/rules/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Rule retired", service.retire(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/draws")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<DrawRow>>> draws(@RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listDraws(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/draws/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<DrawView>> draw(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.getDraw(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/draws")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DrawView>> makeDraw(@Valid @RequestBody DrawRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Draw made", service.draw(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/draws/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DrawView>> close(@PathVariable UUID id, @Valid @RequestBody CloseRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Draw closed", service.close(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/draws/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DrawView>> voidDraw(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Draw voided", service.voidDraw(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/draws/{id}/actions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DrawView>> addAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action recorded", service.addAction(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/actions/{actionId}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<DrawView>> closeAction(@PathVariable UUID actionId, @Valid @RequestBody ActionCloseRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Action closed", service.closeAction(TenantContext.getTenantIdAsObject(), actionId, r, TenantContext.getCurrentUserId(), today())));
    }
}
