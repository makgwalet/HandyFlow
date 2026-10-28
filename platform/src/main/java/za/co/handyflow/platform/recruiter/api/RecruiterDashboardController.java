package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterDashboardService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterDashboardRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterDashboardDtos.Dashboard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/recruiter/dashboard")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Recruiter dashboard")
public class RecruiterDashboardController {

    private final RecruiterDashboardService dashboard;

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Hiring figures for the last 7, 30 or 90 days, compared with the period before")
    public ResponseEntity<ApiResponse<Dashboard>> get(@RequestParam(required = false) Integer days) {
        String problem = RecruiterDashboardRules.daysProblem(days);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PERIOD");
        return ResponseEntity.ok(ApiResponse.success(dashboard.dashboard(TenantContext.getTenantIdAsObject(), RecruiterDashboardRules.daysOrDefault(days), Instant.now())));
    }
}
