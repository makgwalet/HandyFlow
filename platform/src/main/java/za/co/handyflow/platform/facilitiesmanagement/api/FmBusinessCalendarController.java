package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmBusinessCalendarService;
import za.co.handyflow.platform.facilitiesmanagement.dto.AddFmHolidayRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmBusinessCalendarResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmBusinessCalendarRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

/**
 * The business calendar behind BUSINESS_HOURS SLA clocks. Changes need ADMIN or FACILITIESMANAGEMENT_SLA_MANAGE and are
 * audited; they move future deadlines only.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/sla/calendar")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - SLA Calendar", description = "Working hours, working days and public holidays for business-hours SLAs")
public class FmBusinessCalendarController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_CONTRACT_MANAGE','FACILITIESMANAGEMENT_SLA_MANAGE')";
    private static final String CAN_WRITE = "hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_SLA_MANAGE')";

    private final FmBusinessCalendarService calendarService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FmBusinessCalendarResponse>> get() {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(calendarService.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Set working hours, working days and time zone")
    public ResponseEntity<ApiResponse<FmBusinessCalendarResponse>> upsert(@Valid @RequestBody UpsertFmBusinessCalendarRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Calendar saved",
                calendarService.upsert(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/holidays")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<ApiResponse<FmBusinessCalendarResponse>> addHoliday(@Valid @RequestBody AddFmHolidayRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Holiday added",
                calendarService.addHoliday(TenantContext.getTenantIdAsObject(), request)));
    }

    @DeleteMapping("/holidays/{date}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<ApiResponse<FmBusinessCalendarResponse>> removeHoliday(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Holiday removed",
                calendarService.removeHoliday(TenantContext.getTenantIdAsObject(), date)));
    }
}