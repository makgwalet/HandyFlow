package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.DeclarationCampaignService;
import za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus;
import za.co.handyflow.platform.legalcompliance.dto.DeclarationCampaignDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Declaration campaigns (ethics reviewers) and a person's own returns (any signed-in user, so staff need no Legal/Compliance permission to answer). */
@RestController
@RequestMapping("/api/v1/legalcompliance/declaration-campaigns")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Declaration campaigns", description = "Ask everyone to declare their interests, or say they have none")
public class DeclarationCampaignController {

    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_ETHICS_REVIEW','LEGALCOMPLIANCE_ADMIN')";

    private final DeclarationCampaignService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> list(@RequestParam(required = false) CampaignStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CampaignDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CampaignResponse>> launch(@Valid @RequestBody CampaignRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Campaign launched", service.launch(tenant(), req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CampaignResponse>> close(@PathVariable UUID id, @Valid @RequestBody CloseRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Campaign closed", service.close(tenant(), id, req, uid(), uname())));
    }

    @PostMapping({"/{id}/update-audience", "/{id}/add-new-joiners"})
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<SyncResult>> updateAudience(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Audience updated", service.updateAudience(tenant(), id, uid(), uname())));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MyReturn>>> mine() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.mine(tenant(), uid())));
    }

    @PostMapping("/returns/{rid}/answer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyReturn>> answer(@PathVariable UUID rid, @Valid @RequestBody AnswerRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Answer recorded", service.answer(tenant(), rid, req, uid(), uname())));
    }

    @PostMapping("/returns/{rid}/declare")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyReturn>> declare(@PathVariable UUID rid, @Valid @RequestBody EthicsDtos.DeclarationRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declaration recorded", service.declare(tenant(), rid, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
