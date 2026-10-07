package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicBriefingService;
import za.co.handyflow.platform.clinic.dto.PatientBriefing;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic briefing", description = "Everything a clinician needs to know about a patient before the consultation starts")
public class ClinicBriefingController {

    private final ClinicBriefingService briefing;

    @GetMapping("/briefing")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Last visit, vitals, next appointment, follow-up due, medicines, allergies, conditions, lab flags and alerts")
    public ResponseEntity<ApiResponse<PatientBriefing>> get(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                briefing.briefing(TenantContext.getTenantIdAsObject(), patientId)));
    }
}
