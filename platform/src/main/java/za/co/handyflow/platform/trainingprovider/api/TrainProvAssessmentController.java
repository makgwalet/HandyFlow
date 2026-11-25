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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAssessmentService;
import za.co.handyflow.platform.trainingprovider.dto.AssessmentDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/assessment-instruments")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Assessment instruments", description = "Assessment instruments for programme versions")
public class TrainProvAssessmentController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";

    private final TrainProvAssessmentService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<InstrumentView>>> list(@RequestParam(required = false) UUID programmeVersionId,
                                                                  @RequestParam(required = false) UUID moduleId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), programmeVersionId, moduleId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<InstrumentView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<InstrumentView>> create(@Valid @RequestBody InstrumentRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Instrument created", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<InstrumentView>> update(@PathVariable UUID id, @Valid @RequestBody InstrumentUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Instrument updated", service.update(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<InstrumentView>> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Instrument approved",
                service.approve(TenantContext.getTenantIdAsObject(), id, r == null ? null : r.overrideReason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<InstrumentView>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Instrument retired", service.retire(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
