package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ClientAccessService;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** The organisation being served decides what a provider may see. Administrators of this organisation only. */
@RestController
@RequestMapping("/api/v1/legalcompliance/provider-access")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Provider access", description = "Accept, pause or end a compliance provider's access to this organisation")
public class ClientAccessController {

    private static final String ADMIN = "hasAuthority('LEGALCOMPLIANCE_ADMIN')";

    private final ClientAccessService access;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<ProviderAccessResponse>>> list() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(access.list(tenant())));
    }

    @PostMapping("/preview")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<InvitePreview>> preview(@RequestBody CodeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(access.preview(tenant(), req.code())));
    }

    @PostMapping("/accept")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProviderAccessResponse>> accept(@Valid @RequestBody AcceptRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access granted", access.accept(tenant(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProviderAccessResponse>> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access suspended", access.suspend(tenant(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProviderAccessResponse>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access resumed", access.resume(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProviderAccessResponse>> revoke(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access ended", access.revoke(tenant(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<AccessEvent>>> activity(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(access.activity(tenant(), id)));
    }

    public record CodeRequest(String code) {}

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
