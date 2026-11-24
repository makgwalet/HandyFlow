package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceClientPartyService;
import za.co.handyflow.platform.complianceservices.dto.ClientPartyDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client parties and sites", description = "Directors, owners and sites of a client")
public class ComplianceClientPartyController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceClientPartyService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/parties")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<PartiesResponse>> parties(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.parties(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/parties")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PartiesResponse>> addParty(@PathVariable UUID clientId, @Valid @RequestBody SavePartyRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Added",
                service.addParty(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/parties/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PartiesResponse>> updateParty(@PathVariable UUID clientId, @PathVariable UUID id,
                                                                     @Valid @RequestBody SavePartyRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Updated",
                service.updateParty(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/parties/{id}/resign")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PartiesResponse>> resign(@PathVariable UUID clientId, @PathVariable UUID id,
                                                                @RequestBody(required = false) ResignRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Recorded",
                service.resign(TenantContext.getTenantIdAsObject(), clientId, id, request == null ? null : request.resignedOn(),
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/sites")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<SiteResponse>>> sites(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.sites(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/sites")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<SiteResponse>>> addSite(@PathVariable UUID clientId, @Valid @RequestBody SaveSiteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Added",
                service.addSite(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/sites/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<SiteResponse>>> updateSite(@PathVariable UUID clientId, @PathVariable UUID id,
                                                                       @Valid @RequestBody SaveSiteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Updated",
                service.updateSite(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/sites/{id}/deactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<SiteResponse>>> deactivateSite(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Deactivated",
                service.setSiteActive(TenantContext.getTenantIdAsObject(), clientId, id, false, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/sites/{id}/reactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<SiteResponse>>> reactivateSite(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Reactivated",
                service.setSiteActive(TenantContext.getTenantIdAsObject(), clientId, id, true, TenantContext.getCurrentUserId())));
    }
}
