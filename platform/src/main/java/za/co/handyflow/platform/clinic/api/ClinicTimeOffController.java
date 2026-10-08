package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicTimeOffService;
import za.co.handyflow.platform.clinic.dto.TimeOffDtos.CreateTimeOffRequest;
import za.co.handyflow.platform.clinic.dto.TimeOffDtos.TimeOffResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic time off", description = "Leave and other blocks of time when a practitioner cannot be booked")
public class ClinicTimeOffController {

    private final ClinicTimeOffService timeOff;

    @GetMapping("/practitioners/{practitionerId}/time-off")
    @PreAuthorize("hasAuthority('CLINIC_APPOINTMENT_READ')")
    @Operation(summary = "Current and future time off for a practitioner, soonest first")
    public ResponseEntity<ApiResponse<List<TimeOffResponse>>> list(@PathVariable UUID practitionerId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                timeOff.upcoming(TenantContext.getTenantIdAsObject(), practitionerId, Instant.now())));
    }

    @PostMapping("/practitioners/{practitionerId}/time-off")
    @PreAuthorize("hasAuthority('CLINIC_TIME_OFF_WRITE')")
    @Operation(summary = "Block out time when the practitioner cannot be booked. Bookings in that time are refused with 409 unless overridden.")
    public ResponseEntity<ApiResponse<TimeOffResponse>> create(@PathVariable UUID practitionerId,
                                                               @Valid @RequestBody CreateTimeOffRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Time off added",
                timeOff.create(TenantContext.getTenantIdAsObject(), practitionerId, req)));
    }

    @DeleteMapping("/time-off/{id}")
    @PreAuthorize("hasAuthority('CLINIC_TIME_OFF_WRITE')")
    @Operation(summary = "Cancel a block of time off (it is kept in the record as cancelled)")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        timeOff.cancel(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Time off cancelled", null));
    }
}
