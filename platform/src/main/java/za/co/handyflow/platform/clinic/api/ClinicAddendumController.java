package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicAddendumService;
import za.co.handyflow.platform.clinic.dto.AddendumDtos.AddAddendumRequest;
import za.co.handyflow.platform.clinic.dto.AddendumDtos.AddendumResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Addenda on signed consultations. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic/consultations/{consultationId}/addenda")
@RequiredArgsConstructor
@Tag(name = "Clinic addenda", description = "Append-only notes on signed consultations")
public class ClinicAddendumController {

    private final ClinicAddendumService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_CONSULTATION_READ')")
    @Operation(summary = "Addenda of a consultation, oldest first")
    public ResponseEntity<ApiResponse<List<AddendumResponse>>> list(@PathVariable UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.list(TenantContext.getTenantIdAsObject(), consultationId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_CONSULTATION_AMEND')")
    @Operation(summary = "Add an addendum to a SIGNED or LOCKED consultation")
    public ResponseEntity<ApiResponse<AddendumResponse>> add(@PathVariable UUID consultationId,
                                                             @RequestBody AddAddendumRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Addendum added",
                service.add(TenantContext.getTenantIdAsObject(), consultationId, body.text())));
    }
}
