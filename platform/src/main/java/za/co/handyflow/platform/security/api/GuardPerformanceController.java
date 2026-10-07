// security/api/GuardPerformanceController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GuardPerformanceService;
import za.co.handyflow.platform.security.application.internal.GuardRatingService;
import za.co.handyflow.platform.security.application.internal.GuardRiskSettingsService;
import za.co.handyflow.platform.security.application.internal.GuardReviewService;
import za.co.handyflow.platform.security.application.internal.GuardScoreTrendService;
import za.co.handyflow.platform.security.dto.GuardReviewDtos;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Guard performance: the operational score, risk recommendations, ratings and the tenant's risk thresholds.
 * Reading needs SECURITY_READ; adding a rating SECURITY_MANAGE; changing thresholds SECURITY_ADMIN.
 * Recommendations are advice for a person to review. Nothing here changes a guard's status.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Guard performance")
public class GuardPerformanceController {

    private final GuardPerformanceService performanceService;
    private final GuardScoreTrendService trendService;
    private final GuardReviewService reviewService;
    private final GuardRatingService ratingService;
    private final GuardRiskSettingsService settingsService;
    private final FeatureGuard featureGuard;

    @GetMapping("/guards/{guardId}/performance")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Operational score with its working, risk recommendations and recent ratings")
    public ResponseEntity<ApiResponse<PerformanceResponse>> performance(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(performanceService.performance(TenantContext.getTenantIdAsObject(), guardId)));
    }

    @GetMapping("/guards/{guardId}/performance/history")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Daily operational score snapshots, oldest first (days defaults to 90, at most 365)")
    public ResponseEntity<ApiResponse<java.util.List<HistoryPoint>>> history(@PathVariable UUID guardId, @RequestParam(required = false) Integer days) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(trendService.history(TenantContext.getTenantIdAsObject(), guardId, days)));
    }

    @GetMapping("/guards/{guardId}/reviews")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Supervisor reviews of the guard, newest first, with whether the next one is due")
    public ResponseEntity<ApiResponse<GuardReviewDtos.ReviewList>> reviews(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(reviewService.list(TenantContext.getTenantIdAsObject(), guardId)));
    }

    @PostMapping("/guards/{guardId}/reviews")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record a supervisor review; it also counts as a supervisor rating in the operational score")
    public ResponseEntity<ApiResponse<GuardReviewDtos.ReviewItem>> addReview(@PathVariable UUID guardId, @Valid @RequestBody GuardReviewDtos.SaveReviewRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Review recorded",
                reviewService.add(TenantContext.getTenantIdAsObject(), guardId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/guards/{guardId}/ratings")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record a client or supervisor rating (1 to 5 on six dimensions)")
    public ResponseEntity<ApiResponse<RatingItem>> rate(@PathVariable UUID guardId, @Valid @RequestBody SaveRatingRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rating recorded",
                ratingService.add(TenantContext.getTenantIdAsObject(), guardId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/risk-settings")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The thresholds for risk recommendations (defaults until saved)")
    public ResponseEntity<ApiResponse<RiskSettingsDto>> settings() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(settingsService.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/risk-settings")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Change the thresholds for risk recommendations")
    public ResponseEntity<ApiResponse<RiskSettingsDto>> saveSettings(@Valid @RequestBody SaveRiskSettingsRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Risk rules saved",
                settingsService.save(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserName())));
    }
}
