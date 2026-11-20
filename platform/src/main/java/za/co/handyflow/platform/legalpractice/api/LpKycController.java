package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpKycService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Client due diligence (FICA intake): prepare, submit, approve by a second person, review. */
@RestController
@RequestMapping("/api/v1/legal-practice/kyc")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Client due diligence", description = "Identity, beneficial owners, risk rating and review dates for each client")
public class LpKycController {

    private static final String READ = "hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_KYC_PREPARE','LEGALPRACTICE_KYC_APPROVE','LEGALPRACTICE_KYC_APPROVE_SENIOR','LEGALPRACTICE_ADMIN')";
    private static final String PREPARE = "hasAnyAuthority('LEGALPRACTICE_KYC_PREPARE','LEGALPRACTICE_ADMIN')";
    private static final String APPROVE = "hasAnyAuthority('LEGALPRACTICE_KYC_APPROVE','LEGALPRACTICE_KYC_APPROVE_SENIOR','LEGALPRACTICE_ADMIN')";

    private final LpKycService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/overview")
    @PreAuthorize(READ)
    @Operation(summary = "Every client and where their due diligence stands")
    public ResponseEntity<ApiResponse<Page<KycOverviewRow>>> overview(@PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.overview(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize(READ)
    @Operation(summary = "Every cycle of due diligence for one client, newest first")
    public ResponseEntity<ApiResponse<List<KycProfileResponse>>> forClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<KycProfileResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(PREPARE)
    @Operation(summary = "Start due diligence for a client (or a review, when the client has been verified before)")
    public ResponseEntity<ApiResponse<KycProfileResponse>> start(@Valid @RequestBody StartKycRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Due diligence started",
                service.start(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/clients/{clientId}/review")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Open a review of a verified client")
    public ResponseEntity<ApiResponse<KycProfileResponse>> review(@PathVariable UUID clientId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Review opened",
                service.openReview(TenantContext.getTenantIdAsObject(), clientId, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PutMapping("/{id}/assessment")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> assess(@PathVariable UUID id, @RequestBody KycAssessmentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.assess(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/parties")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> addParty(@PathVariable UUID id, @Valid @RequestBody KycPartyRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.addParty(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/parties/{partyId}/verify")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> verifyParty(@PathVariable UUID id, @PathVariable UUID partyId, @Valid @RequestBody VerifyKycPartyRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.verifyParty(TenantContext.getTenantIdAsObject(), id, partyId, req.verifiedOn(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/{id}/parties/{partyId}")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> removeParty(@PathVariable UUID id, @PathVariable UUID partyId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.removeParty(TenantContext.getTenantIdAsObject(), id, partyId)));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> addItem(@PathVariable UUID id, @Valid @RequestBody KycItemRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.addItem(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.removeItem(TenantContext.getTenantIdAsObject(), id, itemId)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Submit a complete due diligence for approval")
    public ResponseEntity<ApiResponse<KycProfileResponse>> submit(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Submitted for approval",
                service.submit(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<KycProfileResponse>> returnToDraft(@PathVariable UUID id, @Valid @RequestBody ReturnKycRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Returned to draft", service.returnToDraft(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Approve a submitted due diligence. A second person; senior management for a high-risk or politically exposed client")
    public ResponseEntity<ApiResponse<KycProfileResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveKycRequest req, Authentication auth) {
        featureGuard.requireModule("legalpractice");
        boolean senior = auth.getAuthorities().stream().anyMatch(a -> "LEGALPRACTICE_KYC_APPROVE_SENIOR".equals(a.getAuthority()) || "LEGALPRACTICE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success("Due diligence verified",
                service.approve(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), senior, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_KYC_SETTINGS','LEGALPRACTICE_KYC_APPROVE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<KycSettingsResponse>> settings() {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_KYC_SETTINGS','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Decide whether matters and trust receipts are blocked until a client is verified. Needs a written reason; kept permanently")
    public ResponseEntity<ApiResponse<KycSettingsResponse>> updateSettings(@Valid @RequestBody UpdateKycSettingsRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Setting saved",
                service.updateSettings(TenantContext.getTenantIdAsObject(), req.enforce(), req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
