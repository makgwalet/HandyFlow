package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelEligibilityService;
import za.co.handyflow.platform.fuel.application.internal.FuelCleaningCertificateService;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Eligibility set-up: what a delivery site lets in, and the specifications of a tanker the site rules are checked against. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Eligibility", description = "Site access rules and tanker specifications")
public class FuelEligibilityController {

    private final FuelEligibilityService eligibility;
    private final FuelCleaningCertificateService certificates;
    private final FeatureGuard featureGuard;

    @GetMapping("/customer-sites/{siteId}/access-rule")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<SiteRuleView>> siteRule(@PathVariable UUID siteId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.siteRule(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PutMapping("/customer-sites/{siteId}/access-rule")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SiteRuleView>> saveSiteRule(@PathVariable UUID siteId, @Valid @RequestBody SiteRuleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Site rule saved", eligibility.saveSiteRule(TenantContext.getTenantIdAsObject(), siteId, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/customer-sites/{siteId}/driver-approvals")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<java.util.List<DriverApprovalView>>> driverApprovals(@PathVariable UUID siteId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.driverApprovals(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PutMapping("/customer-sites/{siteId}/driver-approvals/{employeeId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DriverApprovalView>> saveDriverApproval(@PathVariable UUID siteId, @PathVariable UUID employeeId, @Valid @RequestBody DriverApprovalRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Driver approval saved",
                eligibility.saveDriverApproval(TenantContext.getTenantIdAsObject(), siteId, employeeId, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/customer-sites/{siteId}/driver-approvals/{employeeId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeDriverApproval(@PathVariable UUID siteId, @PathVariable UUID employeeId) {
        featureGuard.requireModule("fuel");
        eligibility.removeDriverApproval(TenantContext.getTenantIdAsObject(), siteId, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Driver approval removed", null));
    }

    @GetMapping("/tankers/{id}/specs")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<TankerSpecsView>> specs(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.specs(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/tankers/{id}/specs")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<TankerSpecsView>> saveSpecs(@PathVariable UUID id, @Valid @RequestBody TankerSpecsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tanker specifications saved", eligibility.saveSpecs(TenantContext.getTenantIdAsObject(), id, req)));
    }

    private static final String MANAGE = "hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')";

    @GetMapping("/tankers/{id}/compartment-ratings")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<java.util.List<CompartmentRatingView>>> ratings(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.compartmentRatings(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/tankers/{id}/compartments/{compartmentId}/rating")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CompartmentRatingView>> saveRating(@PathVariable UUID id, @PathVariable UUID compartmentId, @Valid @RequestBody CompartmentRatingRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Rating saved", eligibility.saveCompartmentRating(TenantContext.getTenantIdAsObject(), id, compartmentId, req)));
    }

    @GetMapping("/tankers/{id}/compartments/{compartmentId}/cleanings")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<java.util.List<CleaningView>>> cleanings(@PathVariable UUID id, @PathVariable UUID compartmentId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.cleanings(TenantContext.getTenantIdAsObject(), id, compartmentId)));
    }

    @PostMapping("/tankers/{id}/compartments/{compartmentId}/cleanings")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CleaningView>> recordCleaning(@PathVariable UUID id, @PathVariable UUID compartmentId, @Valid @RequestBody CleaningRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Cleaning recorded", eligibility.recordCleaning(TenantContext.getTenantIdAsObject(), id, compartmentId, req, TenantContext.getCurrentUserId())));
    }

    private static final String CERT_VIEW = "hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_COMPLIANCE_MANAGE','FUEL_COMPLIANCE_VERIFY')";

    @PostMapping(value = "/tankers/{id}/compartments/{compartmentId}/cleanings/{cleaningId}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CleaningView>> uploadCertificate(@PathVariable UUID id, @PathVariable UUID compartmentId, @PathVariable UUID cleaningId, @RequestParam("file") MultipartFile file) {
        featureGuard.requireModule("fuel");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
        return ResponseEntity.ok(ApiResponse.success("Certificate added", certificates.attach(TenantContext.getTenantIdAsObject(), id, compartmentId, cleaningId, file.getOriginalFilename(), bytes, TenantContext.getCurrentUserId())));
    }

    /** Documents are for the people who manage or verify, like other compliance evidence. */
    @GetMapping("/tankers/{id}/compartments/{compartmentId}/cleanings/{cleaningId}/certificate")
    @PreAuthorize(CERT_VIEW)
    public ResponseEntity<byte[]> certificate(@PathVariable UUID id, @PathVariable UUID compartmentId, @PathVariable UUID cleaningId) {
        featureGuard.requireModule("fuel");
        var f = certificates.download(TenantContext.getTenantIdAsObject(), id, compartmentId, cleaningId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.filename()).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(f.bytes());
    }

    @PostMapping("/tankers/{id}/compartments/{compartmentId}/cleanings/{cleaningId}/verify")
    @PreAuthorize("hasAuthority('FUEL_COMPLIANCE_VERIFY')")
    public ResponseEntity<ApiResponse<CleaningView>> verifyCertificate(@PathVariable UUID id, @PathVariable UUID compartmentId, @PathVariable UUID cleaningId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Verified", certificates.verify(TenantContext.getTenantIdAsObject(), id, compartmentId, cleaningId, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/changeover-rules")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<java.util.List<ChangeoverRuleView>>> changeoverRules() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.changeoverRules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/changeover-rules")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ChangeoverRuleView>> addChangeoverRule(@Valid @RequestBody ChangeoverRuleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Changeover rule added", eligibility.addChangeoverRule(TenantContext.getTenantIdAsObject(), req)));
    }

    @DeleteMapping("/changeover-rules/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> removeChangeoverRule(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        eligibility.removeChangeoverRule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Changeover rule removed", null));
    }

    @GetMapping("/eligibility/settings")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<EligibilitySettings>> settings() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.eligibilitySettings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/eligibility/settings")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EligibilitySettings>> saveSettings(@Valid @RequestBody EligibilitySettings req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", eligibility.saveEligibilitySettings(TenantContext.getTenantIdAsObject(), req)));
    }

    /** The log of loads allocated with review points open. Read only, for the people who make and check those decisions. */
    @GetMapping("/eligibility/overrides")
    @PreAuthorize("hasAnyAuthority('FUEL_ELIGIBILITY_REVIEW','FUEL_COMPLIANCE_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.fuel.dto.EligibilityDtos.OverrideView>>> overrides(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.recentOverrides(TenantContext.getTenantIdAsObject(), limit)));
    }
}
