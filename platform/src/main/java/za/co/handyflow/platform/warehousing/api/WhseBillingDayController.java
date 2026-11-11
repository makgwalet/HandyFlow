package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillableDayRules;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillingDayService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/billing-day-rules")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Billable days", description = "Contract overrides of the storage billable-day rule")
public class WhseBillingDayController {

    private final WhseBillingDayService service;
    private final FeatureGuard featureGuard;

    public record Option(String value, String label) {}
    public record View(List<WhseBillingDayService.Rule> rules, List<WhseBillingDayService.Holiday> holidays, List<Option> options, List<Option> minimumBases, List<WhseBillingDayService.Rule> review) {}
    public record AddRule(UUID clientId, String dayRule, String minimumBasis, LocalDate effectiveFrom, String note) {}
    public record AddHoliday(LocalDate date, String name) {}

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<View>> view() {
        featureGuard.requireModule("warehousing");
        var t = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(new View(service.rules(t), service.holidays(t),
                Arrays.stream(WhseBillableDayRules.DayRule.values()).map(r -> new Option(r.name(), r.label)).toList(),
                Arrays.stream(WhseBillableDayRules.MinimumBasis.values()).map(b -> new Option(b.name(), b.label)).toList(),
                service.reviewList(t, LocalDate.now(ZoneId.of("Africa/Johannesburg"))))));
    }

    /** clientId omitted sets the warehouse default. */
    @PostMapping
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> add(@RequestBody AddRule r) {
        featureGuard.requireModule("warehousing");
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        return ResponseEntity.ok(ApiResponse.success("Saved", service.addRule(TenantContext.getTenantIdAsObject(), r.clientId(), r.dayRule(), r.minimumBasis(), r.effectiveFrom(), r.note(), TenantContext.getCurrentUserId(), today)));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addHoliday(@RequestBody AddHoliday r) {
        featureGuard.requireModule("warehousing");
        service.addHoliday(TenantContext.getTenantIdAsObject(), r.date(), r.name(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    /** Adds the South African public holidays for a year (default: this year) that are not already listed. */
    @PostMapping("/holidays/south-africa")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> addSouthAfricanHolidays(@RequestParam(required = false) Integer year) {
        featureGuard.requireModule("warehousing");
        int y = year != null ? year : LocalDate.now(ZoneId.of("Africa/Johannesburg")).getYear();
        return ResponseEntity.ok(ApiResponse.success("Added", service.addSouthAfricanHolidays(TenantContext.getTenantIdAsObject(), y, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/holidays/{date}")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeHoliday(@PathVariable LocalDate date) {
        featureGuard.requireModule("warehousing");
        service.removeHoliday(TenantContext.getTenantIdAsObject(), date, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }
}
