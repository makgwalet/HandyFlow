package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic", description = "Patient management, appointments and consultations")
public class ClinicController {

    private final ClinicService clinicService;

    // ── Patients ──────────────────────────────────────────────────────────────

    @GetMapping("/patients")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List patients with optional search")
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> getPatients(
            @RequestParam(required = false) String search, Pageable pageable) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatients(tenantId, search, pageable)));
    }

    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get a single patient by ID")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatient(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/patients")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Register a new patient")
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Patient registered",
                clinicService.createPatient(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Practitioners ─────────────────────────────────────────────────────────

    @GetMapping("/practitioners")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List practitioners (paginated)")
    public ResponseEntity<ApiResponse<Page<PractitionerResponse>>> getPractitioners(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPractitioners(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/practitioners/list")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get all active practitioners as a flat list for dropdowns")
    public ResponseEntity<ApiResponse<List<PractitionerResponse>>> getPractitionersList() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPractitionersList(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/practitioners")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Register a practitioner (doctor, physio, dentist, etc.)")
    public ResponseEntity<ApiResponse<PractitionerResponse>> createPractitioner(
            @Valid @RequestBody CreatePractitionerRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Practitioner registered",
                clinicService.createPractitioner(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List appointments, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointments(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getAppointments(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/patients/{patientId}/appointments")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get all appointments for a specific patient")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getPatientAppointments(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatientAppointments(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Book an appointment for a patient")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Appointment booked",
                clinicService.createAppointment(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/appointments/{id}/{action}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Update appointment status: confirm | start | complete | cancel | no_show")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @PathVariable UUID id, @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Appointment updated",
                clinicService.updateAppointmentStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    // ── Consultations ─────────────────────────────────────────────────────────

    @GetMapping("/patients/{patientId}/consultations")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get full consultation history for a patient")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getPatientConsultations(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatientConsultations(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping("/patients/{patientId}/consultations")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Record a consultation with vitals, clinical notes and diagnosis")
    public ResponseEntity<ApiResponse<ConsultationResponse>> createConsultation(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateConsultationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Consultation recorded",
                clinicService.createConsultation(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    // ── Prescriptions ─────────────────────────────────────────────────────────

    @GetMapping("/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get prescriptions issued in a consultation")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getPrescriptions(
            @PathVariable UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getConsultationPrescriptions(TenantContext.getTenantIdAsObject(), consultationId)));
    }

    @PostMapping("/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Add a prescription to a consultation")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> addPrescription(
            @PathVariable UUID consultationId,
            @Valid @RequestBody AddPrescriptionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Prescription added",
                clinicService.addPrescription(TenantContext.getTenantIdAsObject(), consultationId, req)));
    }
}