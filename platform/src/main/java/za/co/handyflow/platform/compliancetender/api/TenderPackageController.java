package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.submission.SubmissionProfileService;
import za.co.handyflow.platform.compliancetender.application.internal.submission.TenderPackageService;
import za.co.handyflow.platform.compliancetender.application.internal.submission.TenderPackageStorage;
import za.co.handyflow.platform.compliancetender.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Submission packages and submission profiles (ADR-005). READ users may preview and build a draft package without
 * pricing; including pricing needs MANAGE or ADMIN (decision 2), decided here from the caller's authorities and handed
 * to the service as a plain flag. Profiles are configuration, so changing them needs MANAGE or ADMIN.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance - Submission packages", description = "Build, list and download tender submission packages; saved submission profiles")
public class TenderPackageController {

    private final TenderPackageService packageService;
    private final SubmissionProfileService profileService;
    private final FeatureGuard featureGuard;

    @PostMapping("/tenders/{id}/packages/preview")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "What a package build would contain and every issue found, without building or storing anything")
    public ResponseEntity<ApiResponse<TenderPackagePlanResponse>> preview(@PathVariable UUID id, @Valid @RequestBody BuildTenderPackageRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(packageService.preview(TenantContext.getTenantIdAsObject(), id, mayIncludePricing(), request)));
    }

    @PostMapping("/tenders/{id}/packages")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Build a new package version (merged PDF plus originals, with manifest and hash)")
    public ResponseEntity<ApiResponse<TenderPackagePlanResponse>> build(@PathVariable UUID id, @Valid @RequestBody BuildTenderPackageRequest request) {
        featureGuard.requireModule("compliancetender");
        TenderPackagePlanResponse result = packageService.build(TenantContext.getTenantIdAsObject(), id, mayIncludePricing(), request,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(result.built() != null ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.built() != null ? "Package built" : "Package not built", result));
    }

    @GetMapping("/tenders/{id}/packages")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<TenderPackageResponse>>> list(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(packageService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/tender-packages/{packageId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderPackageResponse>> get(@PathVariable UUID packageId) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(packageService.get(TenantContext.getTenantIdAsObject(), packageId)));
    }

    @GetMapping("/tender-packages/{packageId}/download")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Download the stored combined PDF; refused if it no longer matches its recorded hash")
    public ResponseEntity<byte[]> download(@PathVariable UUID packageId) {
        featureGuard.requireModule("compliancetender");
        TenderPackageStorage.Loaded file = packageService.download(TenantContext.getTenantIdAsObject(), packageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    // ---- saved submission profiles

    @GetMapping("/submission-profiles")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<SubmissionProfileResponse>>> profiles() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(profileService.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/submission-profiles")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<SubmissionProfileResponse>> createProfile(@Valid @RequestBody SubmissionProfileRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Profile saved",
                profileService.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/submission-profiles/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<SubmissionProfileResponse>> updateProfile(@PathVariable UUID id, @Valid @RequestBody SubmissionProfileRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                profileService.update(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/submission-profiles/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        profileService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Profile deleted", null));
    }

    private static boolean mayIncludePricing() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("COMPLIANCE_MANAGE") || a.getAuthority().equals("COMPLIANCE_ADMIN"));
    }
}
