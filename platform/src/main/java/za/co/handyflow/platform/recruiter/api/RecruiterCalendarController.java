package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterCalendarService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterCalendarRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterCalendarDtos.CalendarInterview;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recruiter/calendar")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Interview calendar")
public class RecruiterCalendarController {

    private final RecruiterCalendarService calendar;

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Interviews between two days (South African time), at most 42 days")
    public ResponseEntity<ApiResponse<List<CalendarInterview>>> between(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String problem = RecruiterCalendarRules.rangeProblem(from, to);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        return ResponseEntity.ok(ApiResponse.success(calendar.between(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
