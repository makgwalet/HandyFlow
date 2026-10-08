package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientClinicalService;
import za.co.handyflow.platform.clinic.dto.PatientClinicalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Structured allergies, conditions and medications. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic patient history", description = "Allergies, conditions and medication list")
public class ClinicPatientClinicalController {

    private final ClinicPatientClinicalService service;

    // ── Allergies ─────────────────────────────────────────────────────────────

    @GetMapping("/allergies")
    @PreAuthorize("hasAuthority('CLINIC_ALLERGY_READ')")
    @Operation(summary = "List allergies (active only unless includeInactive=true)")
    public ResponseEntity<ApiResponse<List<AllergyResponse>>> listAllergies(
            @PathVariable UUID patientId, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.listAllergies(TenantContext.getTenantIdAsObject(), patientId, includeInactive)));
    }

    @PostMapping("/allergies")
    @PreAuthorize("hasAuthority('CLINIC_ALLERGY_WRITE')")
    @Operation(summary = "Record an allergy")
    public ResponseEntity<ApiResponse<AllergyResponse>> addAllergy(
            @PathVariable UUID patientId, @RequestBody AllergyRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Allergy recorded",
                service.addAllergy(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PatchMapping("/allergies/{id}")
    @PreAuthorize("hasAuthority('CLINIC_ALLERGY_WRITE')")
    @Operation(summary = "Update an allergy or change its status (RESOLVED, ENTERED_IN_ERROR)")
    public ResponseEntity<ApiResponse<AllergyResponse>> updateAllergy(
            @PathVariable UUID patientId, @PathVariable UUID id, @RequestBody AllergyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Allergy updated",
                service.updateAllergy(TenantContext.getTenantIdAsObject(), patientId, id, req)));
    }

    // ── Conditions ────────────────────────────────────────────────────────────

    @GetMapping("/conditions")
    @PreAuthorize("hasAuthority('CLINIC_CONDITION_READ')")
    @Operation(summary = "List conditions (active/controlled only unless includeInactive=true)")
    public ResponseEntity<ApiResponse<List<ConditionResponse>>> listConditions(
            @PathVariable UUID patientId, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.listConditions(TenantContext.getTenantIdAsObject(), patientId, includeInactive)));
    }

    @PostMapping("/conditions")
    @PreAuthorize("hasAuthority('CLINIC_CONDITION_WRITE')")
    @Operation(summary = "Record a condition")
    public ResponseEntity<ApiResponse<ConditionResponse>> addCondition(
            @PathVariable UUID patientId, @RequestBody ConditionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Condition recorded",
                service.addCondition(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PatchMapping("/conditions/{id}")
    @PreAuthorize("hasAuthority('CLINIC_CONDITION_WRITE')")
    @Operation(summary = "Update a condition or change its status")
    public ResponseEntity<ApiResponse<ConditionResponse>> updateCondition(
            @PathVariable UUID patientId, @PathVariable UUID id, @RequestBody ConditionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Condition updated",
                service.updateCondition(TenantContext.getTenantIdAsObject(), patientId, id, req)));
    }

    // ── Medications ───────────────────────────────────────────────────────────

    @GetMapping("/medications")
    @PreAuthorize("hasAuthority('CLINIC_MEDICATION_READ')")
    @Operation(summary = "List the medication list (active only unless includeInactive=true)")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> listMedications(
            @PathVariable UUID patientId, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.listMedications(TenantContext.getTenantIdAsObject(), patientId, includeInactive)));
    }

    @PostMapping("/medications")
    @PreAuthorize("hasAuthority('CLINIC_MEDICATION_WRITE')")
    @Operation(summary = "Add a medicine to the patient's medication list")
    public ResponseEntity<ApiResponse<MedicationResponse>> addMedication(
            @PathVariable UUID patientId, @RequestBody MedicationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Medication recorded",
                service.addMedication(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PatchMapping("/medications/{id}")
    @PreAuthorize("hasAuthority('CLINIC_MEDICATION_WRITE')")
    @Operation(summary = "Update, stop or complete a medication")
    public ResponseEntity<ApiResponse<MedicationResponse>> updateMedication(
            @PathVariable UUID patientId, @PathVariable UUID id, @RequestBody MedicationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Medication updated",
                service.updateMedication(TenantContext.getTenantIdAsObject(), patientId, id, req)));
    }
}
