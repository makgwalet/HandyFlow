package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientHistoryService;
import za.co.handyflow.platform.clinic.dto.PatientHistoryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Family history, lifestyle and social history, and medical aid. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic patient background", description = "Family history, lifestyle and social history, medical aid")
public class ClinicPatientHistoryController {

    private final ClinicPatientHistoryService service;

    @GetMapping("/family-history")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_HISTORY_READ')")
    @Operation(summary = "List family history (current entries unless includeInactive=true)")
    public ResponseEntity<ApiResponse<List<FamilyHistoryResponse>>> listFamily(
            @PathVariable UUID patientId, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.listFamily(TenantContext.getTenantIdAsObject(), patientId, includeInactive)));
    }

    @PostMapping("/family-history")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_HISTORY_WRITE')")
    @Operation(summary = "Record a condition in the family")
    public ResponseEntity<ApiResponse<FamilyHistoryResponse>> addFamily(
            @PathVariable UUID patientId, @RequestBody FamilyHistoryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Family history recorded",
                service.addFamily(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PatchMapping("/family-history/{id}")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_HISTORY_WRITE')")
    @Operation(summary = "Change a family history entry, or mark it ENTERED_IN_ERROR")
    public ResponseEntity<ApiResponse<FamilyHistoryResponse>> updateFamily(
            @PathVariable UUID patientId, @PathVariable UUID id, @RequestBody FamilyHistoryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Family history updated",
                service.updateFamily(TenantContext.getTenantIdAsObject(), patientId, id, req)));
    }

    @GetMapping("/social-history")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_HISTORY_READ')")
    @Operation(summary = "Lifestyle and social history (smoking, alcohol, occupation, living situation)")
    public ResponseEntity<ApiResponse<SocialHistoryResponse>> getSocial(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.getSocial(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PutMapping("/social-history")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_HISTORY_WRITE')")
    @Operation(summary = "Save lifestyle and social history")
    public ResponseEntity<ApiResponse<SocialHistoryResponse>> putSocial(
            @PathVariable UUID patientId, @RequestBody SocialHistoryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Social history saved",
                service.putSocial(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @GetMapping("/medical-aid")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_READ')")
    @Operation(summary = "The patient's medical aid; a dependant without their own sees the principal's, marked inherited")
    public ResponseEntity<ApiResponse<MedicalAidResponse>> getMedicalAid(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.getMedicalAid(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PutMapping("/medical-aid")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_UPDATE')")
    @Operation(summary = "Save the patient's own medical aid (scheme and member number are required)")
    public ResponseEntity<ApiResponse<MedicalAidResponse>> putMedicalAid(
            @PathVariable UUID patientId, @RequestBody MedicalAidRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Medical aid saved",
                service.putMedicalAid(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @DeleteMapping("/medical-aid")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_UPDATE')")
    @Operation(summary = "Stop using the patient's own medical aid (the record is kept)")
    public ResponseEntity<ApiResponse<Void>> removeMedicalAid(@PathVariable UUID patientId) {
        service.removeMedicalAid(TenantContext.getTenantIdAsObject(), patientId);
        return ResponseEntity.ok(ApiResponse.success("Medical aid removed", null));
    }
}
