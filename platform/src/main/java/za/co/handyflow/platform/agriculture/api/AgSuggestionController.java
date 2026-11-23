package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgSuggestionService;
import za.co.handyflow.platform.agriculture.dto.SuggestionsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/farms")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Suggestions", description = "Breeds and feed types a farm already uses")
public class AgSuggestionController {

    private final AgSuggestionService suggestionService;
    private final FeatureGuard featureGuard;

    @GetMapping("/{farmId}/suggestions")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Breeds (per species) and feed types already in use on this farm, for type-ahead suggestions")
    public ResponseEntity<ApiResponse<SuggestionsResponse>> forFarm(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(suggestionService.forFarm(TenantContext.getTenantIdAsObject(), farmId)));
    }
}
