package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LicenceService;
import za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.LicenceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Licences, permits and certificates: conditions, renewal and expiry */
@RestController
@RequestMapping("/api/v1/legalcompliance/licences")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Licences", description = "Licences, permits and certificates: conditions, renewal and expiry")
public class LicenceController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_LICENCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_LICENCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final LicenceService licences;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<LicenceResponse>>> list(@RequestParam(required = false) LicenceStatus status, @RequestParam(defaultValue = "false") boolean includeEnded) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(licences.list(tenant(), status, includeEnded)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<LicenceSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(licences.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<LicenceDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(licences.get(tenant(), id)));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<EvidenceRef>>> files(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean renewal) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(licences.files(tenant(), id, renewal)));
    }

    @PostMapping("")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> create(@Valid @RequestBody LicenceRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence created", licences.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> update(@PathVariable UUID id, @Valid @RequestBody LicenceRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence updated", licences.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/renewal/start")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> startRenewal(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Renewal started", licences.startRenewal(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/renewal/abandon")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> abandonRenewal(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Renewal abandoned", licences.abandonRenewal(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/renewal/submit")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> submitRenewal(@PathVariable UUID id, @Valid @RequestBody RenewalSubmitRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Renewal submission recorded", licences.submitRenewal(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/renewal/renewed")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> renewed(@PathVariable UUID id, @Valid @RequestBody RenewedRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Renewal recorded", licences.recordRenewed(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence suspended", licences.suspend(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/reinstate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> reinstate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence reinstated", licences.reinstate(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> revoke(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence revoked", licences.revoke(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/surrender")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> surrender(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Licence surrendered", licences.surrender(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/conditions")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> addCondition(@PathVariable UUID id, @Valid @RequestBody ConditionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Condition added", licences.addCondition(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/{id}/conditions/{cid}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> updateCondition(@PathVariable UUID id, @PathVariable UUID cid, @Valid @RequestBody ConditionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Condition updated", licences.updateCondition(tenant(), id, cid, req, uid(), uname())));
    }

    @PostMapping("/{id}/conditions/{cid}/met")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> meetCondition(@PathVariable UUID id, @PathVariable UUID cid, @Valid @RequestBody MetRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Condition recorded as met", licences.meetCondition(tenant(), id, cid, req, uid(), uname())));
    }

    @PostMapping("/{id}/conditions/{cid}/breach")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> breachCondition(@PathVariable UUID id, @PathVariable UUID cid, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Condition recorded as breached", licences.breachCondition(tenant(), id, cid, req, uid(), uname())));
    }

    @DeleteMapping("/{id}/conditions/{cid}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<LicenceDetail>> removeCondition(@PathVariable UUID id, @PathVariable UUID cid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Condition removed", licences.removeCondition(tenant(), id, cid, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
