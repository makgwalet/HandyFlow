package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/warehousing/settings")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Settings", description = "Operating model and industry profile")
public class WhseSettingsController {

    private final WhseSettingsService service;
    private final FeatureGuard featureGuard;

    public record Option(String value, String label) {}
    public record SettingsView(boolean profileExists, String operatingModel, String industryProfile, boolean defaultLotTracked, boolean defaultExpiryTracked, int activeClients,
                               List<Option> operatingModels, List<Option> industryProfiles) {}
    public record UpdateRequest(@NotNull String operatingModel, @NotNull String industryProfile) {}

    private static SettingsView view(WhseSettingsService.Settings s) {
        return new SettingsView(s.profileExists(), s.operatingModel().name(), s.industryProfile().name(), s.itemDefaults().lotTracked(), s.itemDefaults().expiryTracked(), s.activeClients(),
                Arrays.stream(WhseSettingsRules.OperatingModel.values()).map(m -> new Option(m.name(), m.label)).toList(),
                Arrays.stream(WhseSettingsRules.IndustryProfile.values()).map(p -> new Option(p.name(), p.label)).toList());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<SettingsView>> get() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(view(service.get(TenantContext.getTenantIdAsObject()))));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Change the operating model and industry profile")
    public ResponseEntity<ApiResponse<SettingsView>> update(@Valid @RequestBody UpdateRequest r) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Saved", view(service.update(TenantContext.getTenantIdAsObject(), r.operatingModel(), r.industryProfile(), TenantContext.getCurrentUserId()))));
    }
}
