package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.PriceBookService;
import za.co.handyflow.platform.complianceservices.application.internal.PricingCalculatorService;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Price book", description = "Versioned prices the practice quotes from, written, reviewed and approved by people")
public class PriceBookController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    /** Approving what the practice charges, and seeing what its time costs, are admin decisions. */
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final PriceBookService book;
    private final PricingCalculatorService calculator;
    private final FeatureGuard featureGuard;

    private static boolean isAdmin(Authentication a) {
        return a != null && a.getAuthorities().stream().anyMatch(g -> "COMPLIANCE_SERVICES_ADMIN".equals(g.getAuthority()));
    }

    @GetMapping("/price-book")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<VersionView>>> list(Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(book.list(TenantContext.getTenantIdAsObject(), isAdmin(auth))));
    }

    @PostMapping("/price-book/versions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<VersionView>>> newVersion(Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("New draft started",
                book.newVersion(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PutMapping("/price-book/versions/{versionId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<VersionView>>> saveDraft(@PathVariable UUID versionId, @Valid @RequestBody SaveRequest request, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft saved",
                book.saveDraft(TenantContext.getTenantIdAsObject(), versionId, request, TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/price-book/versions/{versionId}/submit")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<VersionView>>> submit(@PathVariable UUID versionId, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Sent for review",
                book.submit(TenantContext.getTenantIdAsObject(), versionId, TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/price-book/versions/{versionId}/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<VersionView>>> approve(@PathVariable UUID versionId, @Valid @RequestBody ApproveRequest request, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Approved",
                book.approve(TenantContext.getTenantIdAsObject(), versionId, request, TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/price-book/versions/{versionId}/reject")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<VersionView>>> reject(@PathVariable UUID versionId, @Valid @RequestBody RejectRequest request, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Sent back",
                book.reject(TenantContext.getTenantIdAsObject(), versionId, request.reason(), TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/price-book/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<VersionView>>> retire(@RequestBody(required = false) RetireRequest request, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Retired",
                book.retire(TenantContext.getTenantIdAsObject(), request == null ? null : request.on(), TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/pricing/calculate")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CalculateResponse>> calculate(@Valid @RequestBody CalculateRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(calculator.calculate(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/pricing/margin")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<MarginResponse>> margin(@Valid @RequestBody MarginRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(calculator.margin(TenantContext.getTenantIdAsObject(), request)));
    }
}
