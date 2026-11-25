package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAccreditationService;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/accreditation")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Accreditation", description = "Authorities, delivery sites, accreditations and approval scope")
public class TrainProvAccreditationController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvAccreditationService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    // authorities

    @GetMapping("/authorities")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AuthorityView>>> authorities() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.authorities(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/authorities")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuthorityView>> createAuthority(@Valid @RequestBody AuthorityRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Authority added", service.createAuthority(TenantContext.getTenantIdAsObject(), r)));
    }

    @PutMapping("/authorities/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AuthorityView>> updateAuthority(@PathVariable UUID id, @Valid @RequestBody AuthorityRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Authority updated", service.updateAuthority(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/authorities/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<AuthorityView>> retireAuthority(@PathVariable UUID id, @Valid @RequestBody RetireRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Authority retired",
                service.retireAuthority(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    // delivery sites

    @GetMapping("/sites")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<SiteView>>> sites() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.sites(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/sites")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SiteView>> createSite(@Valid @RequestBody SiteRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Site added", service.createSite(TenantContext.getTenantIdAsObject(), r)));
    }

    @PutMapping("/sites/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SiteView>> updateSite(@PathVariable UUID id, @Valid @RequestBody SiteRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Site updated", service.updateSite(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/sites/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<SiteView>> retireSite(@PathVariable UUID id, @Valid @RequestBody RetireRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Site retired",
                service.retireSite(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    // accreditations

    @GetMapping("/accreditations")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AccreditationView>>> list(@RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) UUID authorityId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, authorityId, today())));
    }

    @GetMapping("/accreditations/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AccreditationDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/accreditations")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccreditationView>> create(@Valid @RequestBody CreateAccreditationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Accreditation recorded",
                service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/accreditations/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccreditationView>> edit(@PathVariable UUID id, @Valid @RequestBody EditAccreditationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Accreditation updated",
                service.edit(TenantContext.getTenantIdAsObject(), id, r, today())));
    }

    @PostMapping("/accreditations/{id}/transition")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccreditationView>> transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest r,
                                                                      Authentication auth) {
        featureGuard.requireModule("trainingprovider");
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "TRAININGPROVIDER_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                service.transition(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), admin, today())));
    }

    @PostMapping("/accreditations/{id}/verify")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccreditationView>> verify(@PathVariable UUID id, @Valid @RequestBody VerifyRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Verification recorded",
                service.verify(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    // scope

    @PostMapping("/accreditations/{id}/scopes")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ScopeView>> addScope(@PathVariable UUID id, @Valid @RequestBody ScopeRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Scope added",
                service.addScope(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/scopes/{scopeId}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ScopeView>> withdrawScope(@PathVariable UUID scopeId, @Valid @RequestBody RetireRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Scope withdrawn",
                service.withdrawScope(TenantContext.getTenantIdAsObject(), scopeId, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/coverage")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CoverageView>> coverage(@RequestParam UUID courseId, @RequestParam UUID siteId,
                                                              @RequestParam(required = false) LocalDate on) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                service.coverage(TenantContext.getTenantIdAsObject(), courseId, siteId, on == null ? today() : on)));
    }
}
