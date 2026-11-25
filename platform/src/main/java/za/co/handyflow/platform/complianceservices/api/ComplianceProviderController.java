package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceProviderService;
import za.co.handyflow.platform.complianceservices.dto.ProviderDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Providers", description = "Outside professionals and their accreditations")
public class ComplianceProviderController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceProviderService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() { return LocalDate.now(SAST); }

    @GetMapping("/providers")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ProviderSummary>>> list() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), today())));
    }

    @GetMapping("/providers/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ProviderDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/providers")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> create(@Valid @RequestBody ProviderRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), body, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/providers/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> update(@PathVariable UUID id, @Valid @RequestBody ProviderRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, body, today())));
    }

    @PostMapping("/providers/{id}/deactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> deactivate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.deactivate(TenantContext.getTenantIdAsObject(), id, body.reason(), today())));
    }

    @PostMapping("/providers/{id}/reactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.reactivate(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PutMapping("/providers/{id}/services")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> services(@PathVariable UUID id, @RequestBody ServicesRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.setServices(TenantContext.getTenantIdAsObject(), id, body.serviceCodes(), today())));
    }

    @PostMapping("/providers/{id}/accreditations")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> claim(@PathVariable UUID id, @Valid @RequestBody AccreditationRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.claim(TenantContext.getTenantIdAsObject(), id, body, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-accreditations/{id}/check")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> check(@PathVariable UUID id, @Valid @RequestBody CheckRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.check(TenantContext.getTenantIdAsObject(), id, body.accepted(), body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-accreditations/{id}/retire")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProviderDetail>> retire(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.retire(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), today())));
    }
}
