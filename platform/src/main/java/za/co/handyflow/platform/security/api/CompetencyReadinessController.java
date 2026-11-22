package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.security.application.internal.CompetencyReadinessService;
import za.co.handyflow.platform.security.dto.CompetencyReadinessResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@Tag(name = "Security - Armoury")
@RestController
@RequestMapping("/api/v1/security/armoury/competency-readiness")
@RequiredArgsConstructor
public class CompetencyReadinessController {

    private final CompetencyReadinessService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Is it safe to switch firearm issue to strict competency checking?",
            description = "Lists guards with a firearm competency on record and what each would need under strict mode "
                    + "(a verified certificate naming the category). safeToSwitch is true when nobody who can be issued a "
                    + "firearm today would be refused.")
    public ResponseEntity<ApiResponse<CompetencyReadinessResponse>> report() {
        return ResponseEntity.ok(ApiResponse.success(service.report(TenantContext.getTenantIdAsObject())));
    }
}
