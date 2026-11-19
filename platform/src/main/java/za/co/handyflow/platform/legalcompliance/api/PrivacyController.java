package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.PrivacyService;
import za.co.handyflow.platform.legalcompliance.domain.model.PaiaStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.PiaStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Privacy impact assessments, PAIA requests and regulator filings */
@RestController
@RequestMapping("/api/v1/legalcompliance/privacy")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Privacy & PAIA", description = "Privacy impact assessments, PAIA requests and regulator filings")
public class PrivacyController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_PRIVACY_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_PRIVACY_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('LEGALCOMPLIANCE_PRIVACY_APPROVE','LEGALCOMPLIANCE_ADMIN')";

    private final PrivacyService privacy;
    private final FeatureGuard featureGuard;

    @GetMapping("/pias")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<PiaResponse>>> listPias(@RequestParam(required = false) PiaStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.listPias(tenant(), status)));
    }

    @GetMapping("/pias/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<PiaSummary>> piaSummary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.piaSummary(tenant())));
    }

    @PostMapping("/pias")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PiaResponse>> createPia(@Valid @RequestBody PiaRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment created", privacy.createPia(tenant(), req, uid(), uname())));
    }

    @PutMapping("/pias/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PiaResponse>> editPia(@PathVariable UUID id, @Valid @RequestBody PiaRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment updated", privacy.editPia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/pias/{id}/submit")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PiaResponse>> submitPia(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment submitted", privacy.submitPia(tenant(), id, uid(), uname())));
    }

    @PostMapping("/pias/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<PiaResponse>> approvePia(@PathVariable UUID id, @Valid @RequestBody PiaApproveRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment approved", privacy.approvePia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/pias/{id}/reject")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<PiaResponse>> rejectPia(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment rejected", privacy.rejectPia(tenant(), id, req, uid(), uname())));
    }

    @GetMapping("/paia")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<PaiaResponse>>> listPaia(@RequestParam(required = false) PaiaStatus status, @RequestParam(defaultValue = "false") boolean includeClosed) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.listPaia(tenant(), status, includeClosed)));
    }

    @GetMapping("/paia/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<PaiaSummary>> paiaSummary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.paiaSummary(tenant())));
    }

    @PostMapping("/paia")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> receivePaia(@Valid @RequestBody PaiaRequestBody req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("PAIA request logged", privacy.receivePaia(tenant(), req, uid(), uname())));
    }

    @PutMapping("/paia/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> updatePaia(@PathVariable UUID id, @Valid @RequestBody PaiaRequestBody req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("PAIA request updated", privacy.updatePaia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/paia/{id}/extend")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> extendPaia(@PathVariable UUID id, @Valid @RequestBody PaiaExtendRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Due date extended", privacy.extendPaia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/paia/{id}/fee")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> feePaia(@PathVariable UUID id, @Valid @RequestBody PaiaFeeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Fee recorded", privacy.feePaia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/paia/{id}/decide")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> decidePaia(@PathVariable UUID id, @Valid @RequestBody PaiaDecideRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", privacy.decidePaia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/paia/{id}/complaint")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> complaintPaia(@PathVariable UUID id, @Valid @RequestBody PaiaComplaintRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Complaint recorded", privacy.complaintPaia(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/paia/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PaiaResponse>> closePaia(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Request closed", privacy.closePaia(tenant(), id, uid(), uname())));
    }

    @GetMapping("/filings")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<FilingResponse>>> listFilings(@RequestParam(defaultValue = "false") boolean includeDone) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.listFilings(tenant(), includeDone)));
    }

    @GetMapping("/filings/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FilingSummary>> filingSummary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.filingSummary(tenant())));
    }

    @PostMapping("/filings")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FilingResponse>> planFiling(@Valid @RequestBody FilingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Filing planned", privacy.planFiling(tenant(), req, uid(), uname())));
    }

    @PutMapping("/filings/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FilingResponse>> updateFiling(@PathVariable UUID id, @Valid @RequestBody FilingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Filing updated", privacy.updateFiling(tenant(), id, req, uid(), uname())));
    }

    @GetMapping("/filings/{id}/files")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<EvidenceRef>>> filingFiles(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(privacy.filingFiles(tenant(), id)));
    }

    @PostMapping("/filings/{id}/prepare")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FilingResponse>> prepareFiling(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Marked prepared", privacy.prepareFiling(tenant(), id, uid(), uname())));
    }

    @PostMapping("/filings/{id}/submit")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FilingResponse>> submitFiling(@PathVariable UUID id, @Valid @RequestBody FilingSubmitRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Submission recorded", privacy.submitFiling(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/filings/{id}/acknowledge")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FilingResponse>> acknowledgeFiling(@PathVariable UUID id, @Valid @RequestBody RegulatorRefRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Acknowledgement recorded", privacy.acknowledgeFiling(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
