package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ClientSubmissionService;
import za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.ProviderAccessResponse;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** The organisation being served: the inbox of what providers sent, and what each provider may send. */
@RestController
@RequestMapping("/api/v1/legalcompliance")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Provider inbox", description = "Answer what compliance providers send, and choose what they may send")
public class ClientSubmissionController {

    private static final String READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String ANSWER = "hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String ADMIN = "hasAuthority('LEGALCOMPLIANCE_ADMIN')";

    private final ClientSubmissionService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/provider-submissions")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Inbox>> inbox(@RequestParam(required = false) SubmissionStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.inbox(tenant(), status)));
    }

    @GetMapping("/provider-submissions/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<SubmissionView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.get(tenant(), id)));
    }

    @PostMapping("/provider-submissions/{id}/accept")
    @PreAuthorize(ANSWER)
    public ResponseEntity<ApiResponse<SubmissionView>> accept(@PathVariable UUID id, @Valid @RequestBody RespondRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Accepted", service.accept(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/provider-submissions/{id}/decline")
    @PreAuthorize(ANSWER)
    public ResponseEntity<ApiResponse<SubmissionView>> decline(@PathVariable UUID id, @Valid @RequestBody RespondRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declined", service.decline(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/provider-submissions/{id}/complete")
    @PreAuthorize(ANSWER)
    public ResponseEntity<ApiResponse<SubmissionView>> complete(@PathVariable UUID id, @Valid @RequestBody RespondRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Completed", service.complete(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/provider-access/{id}/grants")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProviderAccessResponse>> grants(@PathVariable UUID id, @Valid @RequestBody ScopesRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access updated", service.changeGrants(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
