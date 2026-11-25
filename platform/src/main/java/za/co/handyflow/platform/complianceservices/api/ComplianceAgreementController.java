package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceAgreementService;
import za.co.handyflow.platform.complianceservices.dto.AgreementDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Agreements", description = "Agreements that follow accepted quotes: scope, term and billing arrangement")
public class ComplianceAgreementController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceAgreementService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/agreements")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<Summary>>> listAll(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.listAll(TenantContext.getTenantIdAsObject(), status, LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/agreements")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<Summary>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.listForClient(TenantContext.getTenantIdAsObject(), clientId, LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/agreements/available-quotes")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<QuoteOption>>> availableQuotes(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.availableQuotes(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/clients/{clientId}/agreements/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), clientId, id, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> create(@PathVariable UUID clientId, @Valid @RequestBody CreateRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Agreement started as a draft",
                service.create(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PutMapping("/clients/{clientId}/agreements/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> save(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody TermsRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft saved", service.save(TenantContext.getTenantIdAsObject(), clientId, id, request, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{id}/activate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> activate(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Agreement is active",
                service.activate(TenantContext.getTenantIdAsObject(), clientId, id, request.note(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{id}/suspend")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> suspend(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Agreement suspended",
                service.suspend(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{id}/resume")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> resume(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Agreement resumed", service.resume(TenantContext.getTenantIdAsObject(), clientId, id, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{id}/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> end(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody EndRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Agreement ended",
                service.end(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<View>> cancel(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft cancelled", service.cancel(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), LocalDate.now())));
    }
}
