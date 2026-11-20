package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ProviderSubmissionService;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** The provider's way of sending observations, recommendations and information requests into a client's inbox. Never edits a client's registers. */
@RestController
@RequestMapping("/api/v1/legalcompliance/provider/clients/{id}/submissions")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Provider submissions", description = "Send observations and recommendations to a client's inbox")
public class ProviderSubmissionController {

    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_PROVIDER_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_USE = "hasAnyAuthority('LEGALCOMPLIANCE_PROVIDER_MANAGE','LEGALCOMPLIANCE_PROVIDER_CONSULT','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_SUBMIT = "hasAnyAuthority('LEGALCOMPLIANCE_PROVIDER_SUBMIT','LEGALCOMPLIANCE_ADMIN')";

    private final ProviderSubmissionService service;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<List<SubmissionView>>> list(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), id, uid(), manager())));
    }

    @PostMapping("")
    @PreAuthorize(CAN_SUBMIT)
    public ResponseEntity<ApiResponse<SubmissionView>> submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Sent to the client", service.submit(tenant(), id, req, uid(), uname(), manager())));
    }

    @PostMapping("/{submissionId}/withdraw")
    @PreAuthorize(CAN_SUBMIT)
    public ResponseEntity<ApiResponse<SubmissionView>> withdraw(@PathVariable UUID id, @PathVariable UUID submissionId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Withdrawn", service.withdraw(tenant(), id, submissionId, uid(), uname(), manager())));
    }

    @PostMapping("/scopes")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Void>> requestScopes(@PathVariable UUID id, @Valid @RequestBody ScopesRequest req) {
        featureGuard.requireModule("legalcompliance");
        service.requestScopes(tenant(), id, req, uid(), uname());
        return ResponseEntity.ok(ApiResponse.success("Request recorded. The client decides whether to grant it", null));
    }

    private static boolean manager() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("LEGALCOMPLIANCE_PROVIDER_MANAGE") || a.getAuthority().equals("LEGALCOMPLIANCE_ADMIN"));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
