package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceContactService;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client contact", description = "Answering client queries and asking clients for documents")
public class ComplianceContactController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceContactService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/portal-queries")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<StaffQuery>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/clients/{clientId}/portal-queries")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<StaffQuery>>> forClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/portal-queries/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<StaffQueryDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/portal-queries/{id}/messages")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<StaffQueryDetail>> reply(@PathVariable UUID id, @Valid @RequestBody BodyRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.reply(TenantContext.getTenantIdAsObject(), id, body.body(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/portal-queries/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<StaffQueryDetail>> close(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.close(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{clientId}/document-requests")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<StaffRequest>>> requests(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.requestsFor(TenantContext.getTenantIdAsObject(), clientId, LocalDate.now(SAST))));
    }

    @PostMapping("/clients/{clientId}/document-requests")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<StaffRequest>> ask(@PathVariable UUID clientId, @Valid @RequestBody DocumentRequestInput in) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.ask(TenantContext.getTenantIdAsObject(), clientId, in, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/document-requests/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<StaffRequest>> closeRequest(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.closeRequest(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/document-requests/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<StaffRequest>> cancelRequest(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.cancelRequest(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }
}
