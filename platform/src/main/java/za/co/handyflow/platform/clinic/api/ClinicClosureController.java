package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicClosureService;
import za.co.handyflow.platform.clinic.dto.ClosureDtos.ClosureResponse;
import za.co.handyflow.platform.clinic.dto.ClosureDtos.CreateClosureRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/closures")
@RequiredArgsConstructor
@Tag(name = "Clinic closures", description = "Whole days when the clinic takes no bookings")
public class ClinicClosureController {

    private final ClinicClosureService closures;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Current and future closures, soonest first")
    public ResponseEntity<ApiResponse<List<ClosureResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", closures.upcoming(TenantContext.getTenantIdAsObject(),
                LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "Close the clinic for one or more whole days. Bookings on those days are refused with 409 unless overridden.")
    public ResponseEntity<ApiResponse<ClosureResponse>> create(@Valid @RequestBody CreateClosureRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Closure added",
                closures.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "Cancel a closure (it is kept in the record as cancelled)")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        closures.cancel(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Closure cancelled", null));
    }
}
