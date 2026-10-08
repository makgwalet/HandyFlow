package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.clinic.application.internal.ClinicDashboardService;
import za.co.handyflow.platform.clinic.dto.DashboardSummary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/clinic/dashboard")
@RequiredArgsConstructor
@Tag(name = "Clinic dashboard", description = "Today's counts and appointment list for the clinic's own day")
public class ClinicDashboardController {

    private final ClinicDashboardService dashboard;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CLINIC_DASHBOARD_READ')")
    @Operation(summary = "Today's appointment counts by status, the day's list, the next booked appointment and the patient total.")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.success("Success", dashboard.summary(TenantContext.getTenantIdAsObject())));
    }
}
