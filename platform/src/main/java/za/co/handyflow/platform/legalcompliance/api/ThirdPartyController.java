package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ThirdPartyService;
import za.co.handyflow.platform.legalcompliance.domain.model.ThirdPartyStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Supplier, partner and contractor compliance: due diligence, certificates, approval and periodic review */
@RestController
@RequestMapping("/api/v1/legalcompliance/third-parties")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Third parties", description = "Supplier, partner and contractor compliance: due diligence, certificates, approval and periodic review")
public class ThirdPartyController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_THIRDPARTY_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_THIRDPARTY_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('LEGALCOMPLIANCE_THIRDPARTY_APPROVE','LEGALCOMPLIANCE_ADMIN')";

    private final ThirdPartyService thirdParties;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ThirdPartyResponse>>> list(@RequestParam(required = false) ThirdPartyStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(thirdParties.list(tenant(), status)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ThirdPartySummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(thirdParties.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(thirdParties.get(tenant(), id)));
    }

    @PostMapping("")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> create(@Valid @RequestBody ThirdPartyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Third party created", thirdParties.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> update(@PathVariable UUID id, @Valid @RequestBody ThirdPartyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Third party updated", thirdParties.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> approve(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Third party approved", thirdParties.approve(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Third party suspended", thirdParties.suspend(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/offboard")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> offboard(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Third party offboarded", thirdParties.offboard(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> review(@PathVariable UUID id, @Valid @RequestBody ThirdPartyReviewRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Review recorded", thirdParties.review(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> addItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item added", thirdParties.addItem(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> updateItem(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ItemRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item updated", thirdParties.updateItem(tenant(), id, itemId, req, uid(), uname())));
    }

    @GetMapping("/{id}/items/{itemId}/files")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<EvidenceRef>>> itemFiles(@PathVariable UUID id, @PathVariable UUID itemId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(thirdParties.itemFiles(tenant(), id, itemId)));
    }

    @PostMapping("/{id}/items/{itemId}/verify")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> verifyItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item verified", thirdParties.verifyItem(tenant(), id, itemId, uid(), uname())));
    }

    @PostMapping("/{id}/items/{itemId}/reject")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> rejectItem(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item rejected", thirdParties.rejectItem(tenant(), id, itemId, req, uid(), uname())));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ThirdPartyDetail>> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item removed", thirdParties.removeItem(tenant(), id, itemId, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
