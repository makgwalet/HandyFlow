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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvSitePermissionService;
import za.co.handyflow.platform.trainingprovider.dto.SitePermissionDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/site-permissions")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Site permissions", description = "Who may deliver, assess or moderate at which site")
public class TrainProvSitePermissionController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvSitePermissionService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PermissionView>>> list(@RequestParam(required = false) UUID siteId, @RequestParam(required = false) UUID practitionerId,
                                                                  @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, practitionerId, status, today())));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PermissionView>> create(@Valid @RequestBody PermissionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Permission recorded", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PermissionView>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Permission withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }
}
