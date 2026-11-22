// security/api/WeeklyHoursController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Weekly hours")
public class WeeklyHoursController {

    private final WeeklyHoursService hours;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/workforce/weekly-hours")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Guards near or over their weekly hours on the roster, with double bookings and short rests",
            description = "The week runs Monday to Sunday in South African time; pass any date in it (default this week). " +
                    "Counts rostered (scheduled, active, completed) hours, merged so a double booking is not counted twice. " +
                    "Thresholds are configurable: handyflow.security.hours.warn-at / limit / ceiling / min-rest. flaggedOnly defaults to true.")
    public ResponseEntity<ApiResponse<WeeklyHoursService.Report>> weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required = false, defaultValue = "true") boolean flaggedOnly) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", hours.report(TenantContext.getTenantIdAsObject(), week, flaggedOnly)));
    }
}
