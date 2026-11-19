package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingDeliverabilityService;
import za.co.handyflow.platform.marketing.dto.DeliverabilityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/marketing/deliverability")
@RequiredArgsConstructor
@Tag(name = "Marketing Deliverability", description = "SPF, DKIM, DMARC and MX checks for the sending domain")
public class MarketingDeliverabilityController {

    private final MarketingDeliverabilityService service;

    @GetMapping
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<State>> state() {
        return ResponseEntity.ok(ApiResponse.success(service.state(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Save the sending domain and DKIM selectors. Clears the stored result if either changed.")
    public ResponseEntity<ApiResponse<State>> save(@Valid @RequestBody SettingsRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
                service.save(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.domain(), req.selectors())));
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Run the DNS checks for the saved domain (20 per hour)")
    public ResponseEntity<ApiResponse<Result>> check() {
        return ResponseEntity.ok(ApiResponse.success("Checked", service.check(TenantContext.getTenantIdAsObject())));
    }
}