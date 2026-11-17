package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelAlertRecipientService;
import za.co.handyflow.platform.fuel.dto.AlertRecipientDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;

/** Who else is told about a Fuel alert. Needs the configuration permission, like access limits. Shared foundation, so no division rule. */
@RestController
@RequestMapping("/api/v1/fuel/alert-recipients")
@RequiredArgsConstructor
@Tag(name = "Fuel Alert Recipients", description = "Choose who is told about Fuel alerts besides administrators")
public class FuelAlertRecipientController {

    private final FuelAlertRecipientService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<List<Topic>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/{topic}")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<Topic>> replace(@PathVariable String topic, @RequestBody SaveRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Recipients saved",
                service.replace(TenantContext.getTenantIdAsObject(), topic.toUpperCase(java.util.Locale.ROOT), req.userIds(), req.excludeAdmins(), TenantContext.getCurrentUserId())));
    }
}
