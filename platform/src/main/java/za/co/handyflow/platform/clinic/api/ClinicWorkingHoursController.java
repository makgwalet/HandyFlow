package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicWorkingHoursService;
import za.co.handyflow.platform.clinic.dto.WorkingHoursDtos.SaveWorkingHoursRequest;
import za.co.handyflow.platform.clinic.dto.WorkingHoursDtos.WindowDto;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic working hours", description = "A practitioner's usual weekly working hours")
public class ClinicWorkingHoursController {

    private final ClinicWorkingHoursService workingHours;

    @GetMapping("/practitioners/{practitionerId}/working-hours")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Weekly working hours. An empty list means the practitioner can be booked at any time.")
    public ResponseEntity<ApiResponse<List<WindowDto>>> get(@PathVariable UUID practitionerId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                workingHours.get(TenantContext.getTenantIdAsObject(), practitionerId)));
    }

    @PutMapping("/practitioners/{practitionerId}/working-hours")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Replace the whole week. Bookings outside these hours are refused with 409 unless overridden.")
    public ResponseEntity<ApiResponse<List<WindowDto>>> save(@PathVariable UUID practitionerId,
                                                             @Valid @RequestBody SaveWorkingHoursRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Working hours saved",
                workingHours.replace(TenantContext.getTenantIdAsObject(), practitionerId, req)));
    }
}
