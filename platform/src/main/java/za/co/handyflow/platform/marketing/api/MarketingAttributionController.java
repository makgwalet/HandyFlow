package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingAttributionService;
import za.co.handyflow.platform.marketing.dto.AttributionDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing/attribution")
@RequiredArgsConstructor
@Tag(name = "Marketing Attribution", description = "Last-click sales attribution, revenue and ROI")
public class MarketingAttributionController {

    private final MarketingAttributionService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Revenue, orders and ROI by campaign for sales in the last N days (one currency)")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "ZAR") String currency) {
        return ResponseEntity.ok(ApiResponse.success(service.overview(TenantContext.getTenantIdAsObject(), days, currency)));
    }

    @GetMapping("/campaigns/{id}/conversions")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Sales credited to a campaign (emails are masked)")
    public ResponseEntity<ApiResponse<Page<ConversionRow>>> conversions(@PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.conversions(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PutMapping("/campaigns/{id}/cost")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Set or clear the cost of a campaign (enables ROI)")
    public ResponseEntity<ApiResponse<Void>> cost(@PathVariable UUID id, @Valid @RequestBody CostRequest req) {
        service.setCost(TenantContext.getTenantIdAsObject(), id, req.costCents());
        return ResponseEntity.ok(ApiResponse.success("Cost saved", null));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<AttributionSettings>> settings() {
        return ResponseEntity.ok(ApiResponse.success(new AttributionSettings(service.windowDays(TenantContext.getTenantIdAsObject().getValue()))));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Attribution window in days (1-30). Applies to sales recorded from now on.")
    public ResponseEntity<ApiResponse<AttributionSettings>> updateSettings(@RequestBody AttributionSettings req) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
                service.updateWindow(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.windowDays())));
    }

    @PostMapping("/conversions")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Record an offline sale by hand (idempotent on externalRef)")
    public ResponseEntity<ApiResponse<RecordResult>> record(@Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Processed", service.recordPayment(TenantContext.getTenantIdAsObject(),
                TenantContext.getCurrentUserId(), req.email(), req.amountCents(), req.currency(), req.externalRef(), req.paidAt(), req.description(), "MANUAL")));
    }

    @PostMapping("/conversions/refund")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Record a refund against an earlier recorded sale")
    public ResponseEntity<ApiResponse<RecordResult>> refund(@Valid @RequestBody RecordRefundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Processed", service.recordRefund(TenantContext.getTenantIdAsObject(),
                TenantContext.getCurrentUserId(), req.originalRef(), req.externalRef(), req.amountCents(), req.refundedAt(), "MANUAL")));
    }
}