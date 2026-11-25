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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvWorkplaceService;
import za.co.handyflow.platform.trainingprovider.dto.WorkplaceDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Workplace learning", description = "Workplace placements and logbooks")
public class TrainProvWorkplaceController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvWorkplaceService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/workplace-placements")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PlacementView>>> list(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), enrolmentId, status)));
    }

    @GetMapping("/workplace-placements/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<PlacementDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/workplace-placements")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlacementView>> create(@Valid @RequestBody PlacementRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Placement added", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/workplace-placements/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlacementView>> update(@PathVariable UUID id, @Valid @RequestBody PlacementUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Placement updated", service.update(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/workplace-placements/{id}/start")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlacementView>> start(@PathVariable UUID id, @Valid @RequestBody DayRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Placement started", service.start(TenantContext.getTenantIdAsObject(), id, r.on(), today())));
    }

    @PostMapping("/workplace-placements/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlacementView>> complete(@PathVariable UUID id, @Valid @RequestBody DayRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Placement completed", service.complete(TenantContext.getTenantIdAsObject(), id, r.on(), today())));
    }

    @PostMapping("/workplace-placements/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlacementView>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Placement cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, r.reason())));
    }

    @PostMapping("/workplace-placements/{id}/logs")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LogView>> addLog(@PathVariable UUID id, @Valid @RequestBody LogRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Entry added", service.addLog(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/workplace-logs/{id}/confirm")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LogView>> confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Mentor confirmation recorded", service.confirm(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/workplace-logs/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LogView>> voidLog(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Entry voided", service.voidLog(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
