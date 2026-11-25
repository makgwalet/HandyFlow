package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortfolioService;
import za.co.handyflow.platform.complianceservices.dto.PortfolioDtos.Summary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/compliance-services/portfolio")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Portfolio", description = "Early warning across clients")
public class CompliancePortfolioController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final CompliancePortfolioService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/early-warning")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Summary>> earlyWarning() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.earlyWarning(TenantContext.getTenantIdAsObject(), LocalDate.now(SAST))));
    }
}
