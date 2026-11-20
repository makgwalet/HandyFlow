package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ProxyService;
import za.co.handyflow.platform.legalcompliance.application.internal.ResolutionVotingService;
import za.co.handyflow.platform.legalcompliance.dto.ProxyDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.VotingDtos.VotingView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Proxies for committee meetings: appoint and revoke before the meeting, record the holder's proxy votes after it. */
@RestController
@RequestMapping("/api/v1/legalcompliance/committees/{id}/meetings/{mid}")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Proxies", description = "Proxy appointments and proxy votes for committee meetings")
public class ProxyController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ProxyService proxies;
    private final ResolutionVotingService voting;
    private final FeatureGuard featureGuard;

    @GetMapping("/proxies")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ProxyView>>> list(@PathVariable UUID id, @PathVariable UUID mid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(proxies.list(tenant(), id, mid)));
    }

    @PostMapping("/proxies")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProxyView>> appoint(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody AppointRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Proxy recorded", proxies.appoint(tenant(), id, mid, req, uid(), uname())));
    }

    @PostMapping("/proxies/{pid}/revoke")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProxyView>> revoke(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID pid, @Valid @RequestBody RevokeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Proxy revoked", proxies.revoke(tenant(), id, mid, pid, req, uid(), uname())));
    }

    @GetMapping("/resolutions/{rid}/proxy-votes")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ProxyVotingView>> proxyVotes(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(voting.proxyView(tenant(), id, mid, rid)));
    }

    @PutMapping("/resolutions/{rid}/proxy-votes")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VotingView>> castProxyVotes(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody ProxyCastRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Proxy votes recorded", voting.castProxy(tenant(), id, mid, rid, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
