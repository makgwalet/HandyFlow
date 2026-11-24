package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceWorkQueueService;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.WorkQueueResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/compliance-services/work-queue")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Work queue", description = "What needs attention across the practice's clients")
public class ComplianceWorkQueueController {

    private final ComplianceWorkQueueService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<WorkQueueResponse>> queue(Authentication authentication) {
        featureGuard.requireModule("complianceservices");
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "COMPLIANCE_SERVICES_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(service.queue(TenantContext.getTenantIdAsObject(), admin, LocalDate.now())));
    }
}
