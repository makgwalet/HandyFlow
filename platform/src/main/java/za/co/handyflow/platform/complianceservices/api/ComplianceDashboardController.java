package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceDashboardService;
import za.co.handyflow.platform.complianceservices.dto.DashboardDtos.Summary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/compliance-services/dashboard")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Dashboard", description = "The practice at a glance")
public class ComplianceDashboardController {

    private final ComplianceDashboardService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<Summary>> summary(Authentication authentication) {
        featureGuard.requireModule("complianceservices");
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "COMPLIANCE_SERVICES_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject(), admin, LocalDate.now())));
    }
}
