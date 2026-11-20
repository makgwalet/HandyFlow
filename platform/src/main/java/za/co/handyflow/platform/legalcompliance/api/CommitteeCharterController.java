package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.CommitteeCharterService;
import za.co.handyflow.platform.legalcompliance.dto.CharterDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** A committee's charter rules: anyone who can read committees sees them; only LEGALCOMPLIANCE_CHARTER_MANAGE or ADMIN records a new version. */
@RestController
@RequestMapping("/api/v1/legalcompliance/committees/{id}/charter")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Committee charter", description = "Charter rules that decide how a committee's votes are counted")
public class CommitteeCharterController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_CHARTER_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_RECORD = "hasAnyAuthority('LEGALCOMPLIANCE_CHARTER_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final CommitteeCharterService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<CharterView>> view(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.view(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_RECORD)
    public ResponseEntity<ApiResponse<CharterView>> record(@PathVariable UUID id, @Valid @RequestBody RecordCharterRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Charter rules recorded", service.record(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
