package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpProfileService;
import za.co.handyflow.platform.legalpractice.dto.LpProfileResponse;
import za.co.handyflow.platform.legalpractice.dto.UpsertLpProfileRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/legal-practice/profile")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Profile", description = "The firm's own practice profile")
public class LpProfileController {

    private final LpProfileService profileService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Get the firm's own practice profile")
    public ResponseEntity<ApiResponse<LpProfileResponse>> getProfile() {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                profileService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Create or update the firm's practice profile")
    public ResponseEntity<ApiResponse<LpProfileResponse>> upsertProfile(@Valid @RequestBody UpsertLpProfileRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                profileService.upsertProfile(TenantContext.getTenantIdAsObject(), req)));
    }
}
