package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.EthicsService;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Conflict-of-interest declarations and the gifts and hospitality register */
@RestController
@RequestMapping("/api/v1/legalcompliance/ethics")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Ethics", description = "Conflict-of-interest declarations and the gifts and hospitality register")
public class EthicsController {

    private static final String CAN_USE = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ETHICS_REVIEW','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_REVIEW = "hasAnyAuthority('LEGALCOMPLIANCE_ETHICS_REVIEW','LEGALCOMPLIANCE_ADMIN')";

    private final EthicsService ethics;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<EthicsSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(ethics.summary(tenant())));
    }

    @GetMapping("/declarations")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<List<DeclarationResponse>>> declarations(@RequestParam(required = false) DeclarationStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(ethics.declarations(tenant(), uid(), seeAll(), status)));
    }

    @GetMapping("/declarations/{id}")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<DeclarationResponse>> declaration(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(ethics.declaration(tenant(), id, uid(), seeAll())));
    }

    @PostMapping("/declarations")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<DeclarationResponse>> declare(@Valid @RequestBody DeclarationRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declaration recorded", ethics.declare(tenant(), req, uid(), uname())));
    }

    @PutMapping("/declarations/{id}")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<DeclarationResponse>> amend(@PathVariable UUID id, @Valid @RequestBody DeclarationRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declaration amended", ethics.amend(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/declarations/{id}/confirm")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<DeclarationResponse>> confirm(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Confirmed", ethics.confirmUnchanged(tenant(), id, uid(), uname())));
    }

    @PostMapping("/declarations/{id}/withdraw")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<DeclarationResponse>> withdraw(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declaration withdrawn", ethics.withdraw(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/declarations/{id}/review")
    @PreAuthorize(CAN_REVIEW)
    public ResponseEntity<ApiResponse<DeclarationResponse>> startReview(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Review started", ethics.startReview(tenant(), id, uid(), uname())));
    }

    @PostMapping("/declarations/{id}/decide")
    @PreAuthorize(CAN_REVIEW)
    public ResponseEntity<ApiResponse<DeclarationResponse>> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", ethics.decide(tenant(), id, req, uid(), uname())));
    }

    @GetMapping("/gifts")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<List<GiftResponse>>> gifts(@RequestParam(required = false) GiftStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(ethics.gifts(tenant(), uid(), seeAll(), status)));
    }

    @PostMapping("/gifts")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<GiftResponse>> recordGift(@Valid @RequestBody GiftRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Entry recorded", ethics.recordGift(tenant(), req, uid(), uname())));
    }

    @PostMapping("/gifts/{id}/approve")
    @PreAuthorize(CAN_REVIEW)
    public ResponseEntity<ApiResponse<GiftResponse>> approveGift(@PathVariable UUID id, @RequestBody(required = false) GiftDecisionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Approved", ethics.approveGift(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/gifts/{id}/decline")
    @PreAuthorize(CAN_REVIEW)
    public ResponseEntity<ApiResponse<GiftResponse>> declineGift(@PathVariable UUID id, @RequestBody GiftDecisionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Declined", ethics.declineGift(tenant(), id, req, uid(), uname())));
    }

    /** Reviewers see everyone's entries; everyone else sees only their own. */
    private static boolean seeAll() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("LEGALCOMPLIANCE_ETHICS_REVIEW") || a.getAuthority().equals("LEGALCOMPLIANCE_ADMIN"));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
