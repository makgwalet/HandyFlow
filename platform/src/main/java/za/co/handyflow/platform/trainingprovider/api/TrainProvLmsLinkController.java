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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLmsLinkService;
import za.co.handyflow.platform.trainingprovider.dto.LmsLinkDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/lms-links")
@RequiredArgsConstructor
@Tag(name = "Training Provider - External LMS links", description = "Launch links to an outside learning system")
public class TrainProvLmsLinkController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";

    private final TrainProvLmsLinkService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LinkView>>> list(@RequestParam(required = false) UUID programmeVersionId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), programmeVersionId, status)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LinkView>> create(@Valid @RequestBody LinkRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Link added", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LinkView>> update(@PathVariable UUID id, @Valid @RequestBody LinkUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Link updated", service.update(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LinkView>> end(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Link ended", service.end(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
