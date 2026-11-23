package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgCalendarService;
import za.co.handyflow.platform.agriculture.dto.CalendarItemResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/farms")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Calendar", description = "The farm's own calendar, built from dates the farm already records")
public class AgCalendarController {

    private final AgCalendarService calendarService;
    private final FeatureGuard featureGuard;

    @GetMapping("/{farmId}/calendar")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "What is due on the farm between two dates (62 days at most)",
            description = "Health reminders and scheduled treatments, the day each withdrawal period ends, births expected, plantings planned, " +
                    "harvests expected and scouting follow-ups, oldest first. Read only. Low stock has no date, so it is not here.")
    public ResponseEntity<ApiResponse<List<CalendarItemResponse>>> calendar(
            @PathVariable UUID farmId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(calendarService.getCalendar(TenantContext.getTenantIdAsObject(), farmId, from, to)));
    }
}
