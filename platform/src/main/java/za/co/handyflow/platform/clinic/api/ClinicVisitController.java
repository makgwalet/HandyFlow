package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicVisitService;
import za.co.handyflow.platform.clinic.dto.VisitDtos.VisitResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** The visit history of a patient with notes, prescriptions, addenda and the people involved. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic visits", description = "Visit history")
public class ClinicVisitController {

    private final ClinicVisitService service;

    @GetMapping("/visits")
    @PreAuthorize("hasAuthority('CLINIC_CONSULTATION_READ')")
    @Operation(summary = "Visits newest first, with notes, prescriptions, addenda and who prepared, reviewed and signed each")
    public ResponseEntity<ApiResponse<List<VisitResponse>>> visits(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.visits(TenantContext.getTenantIdAsObject(), patientId)));
    }
}
