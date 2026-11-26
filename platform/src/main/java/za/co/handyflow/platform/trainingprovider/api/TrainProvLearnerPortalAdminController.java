package za.co.handyflow.platform.trainingprovider.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnerPortalService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerPortalGrant;
import za.co.handyflow.platform.trainingprovider.dto.LearnerPortalDtos.*;

import java.util.List;
import java.util.UUID;

/** Staff side: who may see a learner's own records in the learner portal. */
@RestController
@RequestMapping("/api/v1/training-provider/learners/{learnerId}/portal-access")
@RequiredArgsConstructor
public class TrainProvLearnerPortalAdminController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";

    private final TrainProvLearnerPortalService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<GrantResponse>>> list(@PathVariable UUID learnerId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), learnerId).stream().map(this::map).toList()));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<GrantResponse>> invite(@PathVariable UUID learnerId, @Valid @RequestBody InviteRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Invite sent", map(service.invite(TenantContext.getTenantIdAsObject(), learnerId, r.email(), r.reason(), TenantContext.getCurrentUserId()))));
    }

    @PostMapping("/{grantId}/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<GrantResponse>> end(@PathVariable UUID learnerId, @PathVariable UUID grantId, @Valid @RequestBody EndRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Access ended", map(service.end(TenantContext.getTenantIdAsObject(), grantId, r.reason(), TenantContext.getCurrentUserId()))));
    }

    private GrantResponse map(TrainProvLearnerPortalGrant g) {
        return new GrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(), g.getInviteReason(), g.getAcceptedAt(), g.getEndedReason(), g.getCreatedAt());
    }
}
