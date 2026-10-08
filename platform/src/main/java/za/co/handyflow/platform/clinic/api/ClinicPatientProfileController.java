package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientProfileService;
import za.co.handyflow.platform.clinic.dto.ProfileDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** The patient profile page. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{id}")
@RequiredArgsConstructor
@Tag(name = "Clinic patient profile", description = "Profile details, name and ID corrections, contact details and completeness")
public class ClinicPatientProfileController {

    private final ClinicPatientProfileService profiles;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_READ')")
    @Operation(summary = "Profile details and what is still missing")
    public ResponseEntity<ApiResponse<ProfileResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", profiles.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_UPDATE')")
    @Operation(summary = "Save the profile details (the whole profile is replaced)")
    public ResponseEntity<ApiResponse<ProfileResponse>> put(@PathVariable UUID id, @RequestBody ProfileRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Profile saved", profiles.put(TenantContext.getTenantIdAsObject(), id, body)));
    }

    @PutMapping("/demographics")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_DEMOGRAPHICS_WRITE')")
    @Operation(summary = "Correct name, ID number, date of birth and sex")
    public ResponseEntity<ApiResponse<PatientCore>> demographics(@PathVariable UUID id, @RequestBody DemographicsRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Details saved", profiles.updateDemographics(TenantContext.getTenantIdAsObject(), id, body)));
    }

    @GetMapping("/corrections")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_DEMOGRAPHICS_READ')")
    @Operation(summary = "Who corrected the name, ID number, date of birth or sex, and what it was before")
    public ResponseEntity<ApiResponse<java.util.List<CorrectionView>>> corrections(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", profiles.corrections(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/contact")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_UPDATE')")
    @Operation(summary = "Save phone, email and the first emergency contact")
    public ResponseEntity<ApiResponse<PatientCore>> contact(@PathVariable UUID id, @RequestBody ContactRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Contact details saved", profiles.updateContact(TenantContext.getTenantIdAsObject(), id, body)));
    }
}
