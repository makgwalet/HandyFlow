package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceQuoteService;
import za.co.handyflow.platform.complianceservices.dto.QuoteDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Quotes", description = "Quotes priced by the server from the price book, with discount approval")
public class ComplianceQuoteController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    /** A discount above the consultant limit is approved by an administrator. */
    private static final String APPROVE = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceQuoteService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/quotes")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<QuoteSummary>>> listAll(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.listAll(TenantContext.getTenantIdAsObject(), status, LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/quotes")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<QuoteSummary>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.listForClient(TenantContext.getTenantIdAsObject(), clientId, LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/quotes/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<QuoteView>> get(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), clientId, id, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> create(@PathVariable UUID clientId, @Valid @RequestBody SaveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Quote started as a draft",
                service.create(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PutMapping("/clients/{clientId}/quotes/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> save(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody SaveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft saved",
                service.save(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/submit")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> submit(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Submitted",
                service.submit(TenantContext.getTenantIdAsObject(), clientId, id, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<QuoteView>> approve(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Approved",
                service.approve(TenantContext.getTenantIdAsObject(), clientId, id, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<QuoteView>> reject(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Sent back",
                service.reject(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/sent")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> markSent(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Marked as sent",
                service.markSent(TenantContext.getTenantIdAsObject(), clientId, id, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/accept")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> accept(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Acceptance recorded",
                service.accept(TenantContext.getTenantIdAsObject(), clientId, id, request.note(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/decline")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> decline(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Decline recorded",
                service.decline(TenantContext.getTenantIdAsObject(), clientId, id, request.note(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/quotes/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QuoteView>> withdraw(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Withdrawn",
                service.withdraw(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }
}
