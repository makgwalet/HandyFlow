package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.EcosystemService;
import za.co.handyflow.platform.legalcompliance.dto.EcosystemDtos.Ecosystem;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Compliance counts reported by other HandyFlow modules */
@RestController
@RequestMapping("/api/v1/legalcompliance/ecosystem")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Across HandyFlow", description = "Read-only compliance counts published by other modules")
public class EcosystemController {

    // Counts only, but they come from modules the reader may have no access to, so this is limited to people who manage compliance.
    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final EcosystemService ecosystem;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Ecosystem>> overview() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(ecosystem.overview(TenantContext.getTenantIdAsObject())));
    }
}
