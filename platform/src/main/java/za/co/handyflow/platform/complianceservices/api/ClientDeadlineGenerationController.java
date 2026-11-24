package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ClientDeadlineGenerationService;
import za.co.handyflow.platform.complianceservices.dto.DeadlineGenerationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Formula deadlines", description = "Deadlines worked out from obligations' due rules")
public class ClientDeadlineGenerationController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ClientDeadlineGenerationService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/key-dates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<KeyDatesView>> keyDates(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.keyDates(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PutMapping("/key-dates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<KeyDatesView>> setKeyDates(@PathVariable UUID clientId, @RequestBody KeyDatesRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Key dates saved",
                service.setKeyDates(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/deadlines/generate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<GenerationResult>> generate(@PathVariable UUID clientId, @RequestBody(required = false) GenerateRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Deadlines updated",
                service.generate(TenantContext.getTenantIdAsObject(), clientId, request == null ? null : request.months(), TenantContext.getCurrentUserId(), LocalDate.now())));
    }
}
