package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkPeriodService;
import za.co.handyflow.platform.bookkeeping.dto.BkPeriodResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Periods", description = "A client's monthly bookkeeping periods — OPEN periods accept new journal entries, CLOSED ones don't")
public class BkPeriodController {

    private final BkPeriodService periodService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/periods")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkPeriodResponse>>> getPeriods(
            @PathVariable UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                periodService.getPeriods(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/periods/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkPeriodResponse>> getPeriod(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(periodService.getPeriod(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkPeriodResponse>> close(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Period closed",
                periodService.close(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/periods/{id}/reopen")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkPeriodResponse>> reopen(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Period reopened",
                periodService.reopen(TenantContext.getTenantIdAsObject(), id)));
    }
}
