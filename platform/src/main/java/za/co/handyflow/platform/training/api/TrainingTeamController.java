package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingTeamService;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Team;

import java.security.Principal;
import java.util.UUID;

/** A manager's view of their direct reports' training. Needs only a signed-in login; the team is found from the login's own employee record. */
@RestController
@RequestMapping("/api/v1/training/team")
@RequiredArgsConstructor
@Tag(name = "Training - My team", description = "A manager's team: certificates, bookings and required training")
public class TrainingTeamController {

    private final TrainingTeamService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Team>> team(Principal principal) {
        featureGuard.requireModule("training");
        UUID user;
        try { user = UUID.fromString(principal.getName()); }
        catch (RuntimeException e) { throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified."); }
        return ResponseEntity.ok(ApiResponse.success(service.team(TenantContext.getTenantIdAsObject(), user)));
    }
}
