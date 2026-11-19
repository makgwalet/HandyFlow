package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.FicaService;
import za.co.handyflow.platform.legalcompliance.domain.model.EscalationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.FicaPartyStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** FICA profile, customer due diligence and unusual-activity escalations (restricted to FICA managers) */
@RestController
@RequestMapping("/api/v1/legalcompliance/fica")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - FICA", description = "FICA profile, customer due diligence and unusual-activity escalations (restricted to FICA managers)")
public class FicaController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_FICA_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_FICA_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final FicaService fica;
    private final FeatureGuard featureGuard;

    @GetMapping("/profile")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaProfileResponse>> profile() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(fica.profile(tenant())));
    }

    @PutMapping("/profile")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaProfileResponse>> saveProfile(@Valid @RequestBody FicaProfileRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("FICA profile saved", fica.saveProfile(tenant(), req, uid(), uname())));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(fica.summary(tenant())));
    }

    @GetMapping("/parties")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<FicaPartyResponse>>> listParties(@RequestParam(required = false) FicaPartyStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(fica.listParties(tenant(), status)));
    }

    @GetMapping("/parties/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> getParty(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(fica.getParty(tenant(), id)));
    }

    @PostMapping("/parties")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> onboard(@Valid @RequestBody FicaPartyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Party onboarded", fica.onboard(tenant(), req, uid(), uname())));
    }

    @PutMapping("/parties/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> update(@PathVariable UUID id, @Valid @RequestBody FicaPartyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Party updated", fica.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/screened")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> screened(@PathVariable UUID id, @Valid @RequestBody ScreenedRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Screening recorded", fica.screened(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/verify")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> verify(@PathVariable UUID id, @RequestBody(required = false) FicaVerifyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Party verified", fica.verify(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/reopen")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> reopen(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Party reopened", fica.reopen(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/decline")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> decline(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Party declined", fica.decline(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/owners")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> addOwner(@PathVariable UUID id, @Valid @RequestBody OwnerRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Owner added", fica.addOwner(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/parties/{id}/owners/{ownerId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> updateOwner(@PathVariable UUID id, @PathVariable UUID ownerId, @Valid @RequestBody OwnerRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Owner updated", fica.updateOwner(tenant(), id, ownerId, req, uid(), uname())));
    }

    @PostMapping("/parties/{id}/owners/{ownerId}/verify")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> verifyOwner(@PathVariable UUID id, @PathVariable UUID ownerId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Owner verified", fica.verifyOwner(tenant(), id, ownerId, uid(), uname())));
    }

    @DeleteMapping("/parties/{id}/owners/{ownerId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FicaPartyResponse>> removeOwner(@PathVariable UUID id, @PathVariable UUID ownerId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Owner removed", fica.removeOwner(tenant(), id, ownerId, uid(), uname())));
    }

    @GetMapping("/escalations")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<EscalationResponse>>> listEscalations(@RequestParam(required = false) EscalationStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(fica.listEscalations(tenant(), status)));
    }

    @PostMapping("/escalations")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<EscalationResponse>> raise(@Valid @RequestBody EscalationRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Escalation raised", fica.raise(tenant(), req, uid(), uname())));
    }

    @PostMapping("/escalations/{id}/review")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<EscalationResponse>> startReview(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Review started", fica.startReview(tenant(), id, uid(), uname())));
    }

    @PostMapping("/escalations/{id}/decide")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<EscalationResponse>> decide(@PathVariable UUID id, @Valid @RequestBody EscalationDecideRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", fica.decide(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
