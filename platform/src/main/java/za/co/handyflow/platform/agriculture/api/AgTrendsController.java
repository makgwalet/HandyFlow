package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsService;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/trends")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Trends", description = "Monthly cost, harvest and livestock-event trends with 30-day comparisons")
public class AgTrendsController {

    private final AgTrendsService trendsService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Monthly trends and last-30-days comparisons for one farm, or all farms when farmId is omitted",
            description = "months is 1 to 24 (default 12); the last month is the current, partial one. Computed from dated records: " +
                    "costs (same definitions as the cost reports), harvests (converted to tonnes), births and deaths. Herd size over " +
                    "time and revenue, labour and equipment cost are not recorded, so they are not available.")
    public ResponseEntity<ApiResponse<AgTrendsResponse>> getTrends(
            @RequestParam(required = false) UUID farmId,
            @RequestParam(defaultValue = "12") int months) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                trendsService.getTrends(TenantContext.getTenantIdAsObject(), farmId, months)));
    }
}
