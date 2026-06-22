package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.domain.model.ClinicMedicationCatalogue;
import za.co.handyflow.platform.clinic.domain.repository.ClinicMedicationCatalogueRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicProcedureCatalogueRepository;
import za.co.handyflow.platform.clinic.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;        // FIX #2 — was missing
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic", description = "Patient management, appointments and consultations")
public class ClinicController {

    private final ClinicService                      clinicService;
    private final ClinicPdfService                   clinicPdfService;
    private final ClinicMedicationCatalogueRepository medicationRepo;
    private final ClinicProcedureCatalogueRepository procedureRepo;

    // ── Patients ──────────────────────────────────────────────────────────────

    // FIX #1 — removed duplicate @GetMapping("/patients").
    // Only the extended version with principalId / includeArchived params is kept.
    @GetMapping("/patients")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List patients — filter by search, principalId, archived status")
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> getPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID principalId,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            Pageable pageable) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatients(tenantId, search, principalId, includeArchived, pageable)));
    }

    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get a single patient by ID")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatient(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/patients")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Register a new patient")
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Patient registered",
                clinicService.createPatient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PatchMapping("/patients/{id}")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Partial update — account type, active status, archive, family linkage")
    public ResponseEntity<ApiResponse<PatientResponse>> patchPatient(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(ApiResponse.success("Patient updated",
                clinicService.patchPatient(TenantContext.getTenantIdAsObject(), id, updates)));
    }

    @GetMapping("/patients/{id}/family")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get all family members linked to this patient")
    public ResponseEntity<ApiResponse<List<PatientResponse>>> getFamily(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getFamilyMembers(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Practitioners ─────────────────────────────────────────────────────────

    @GetMapping("/practitioners")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List practitioners (paginated)")
    public ResponseEntity<ApiResponse<Page<PractitionerResponse>>> getPractitioners(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPractitioners(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/practitioners/list")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get all active practitioners as a flat list for dropdowns")
    public ResponseEntity<ApiResponse<List<PractitionerResponse>>> getPractitionersList() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPractitionersList(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/practitioners")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Register a practitioner (doctor, physio, dentist, etc.)")
    public ResponseEntity<ApiResponse<PractitionerResponse>> createPractitioner(
            @Valid @RequestBody CreatePractitionerRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Practitioner registered",
                clinicService.createPractitioner(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List appointments, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAppointments(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getAppointments(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/patients/{patientId}/appointments")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get all appointments for a specific patient")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getPatientAppointments(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatientAppointments(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Book an appointment for a patient")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Appointment booked",
                clinicService.createAppointment(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/appointments/{id}/{action}")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Update appointment status: confirm | start | complete | cancel | no_show")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @PathVariable UUID id, @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Appointment updated",
                clinicService.updateAppointmentStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    // ── Consultations ─────────────────────────────────────────────────────────

    @GetMapping("/patients/{patientId}/consultations")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get full consultation history for a patient")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getPatientConsultations(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getPatientConsultations(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping("/patients/{patientId}/consultations")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Record a consultation with vitals, clinical notes and diagnosis")
    public ResponseEntity<ApiResponse<ConsultationResponse>> createConsultation(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateConsultationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Consultation recorded",
                clinicService.createConsultation(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    // ── Prescriptions ─────────────────────────────────────────────────────────

    @GetMapping("/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get prescriptions issued in a consultation")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getPrescriptions(
            @PathVariable UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getConsultationPrescriptions(TenantContext.getTenantIdAsObject(), consultationId)));
    }

    @PostMapping("/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasAuthority('CLINIC_PRESCRIPTION_WRITE')")
    @Operation(summary = "Add a prescription to a consultation")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> addPrescription(
            @PathVariable UUID consultationId,
            @Valid @RequestBody AddPrescriptionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Prescription added",
                clinicService.addPrescription(TenantContext.getTenantIdAsObject(), consultationId, req)));
    }

    // ── Medical Certificate PDF ───────────────────────────────────────────────

    @PostMapping("/consultations/{id}/medical-certificate")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Generate a medical certificate PDF for a consultation")
    public ResponseEntity<byte[]> generateMedicalCertificate(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate unfitFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate unfitTo,
            @RequestParam(required = false) String notes) {
        byte[] pdf = clinicPdfService.generateMedicalCertificate(
                TenantContext.getTenantIdAsObject(), id, unfitFrom, unfitTo, notes);
        return pdfResponse(pdf, "medical-certificate-" + id + ".pdf");
    }

    // ── Prescription PDF ──────────────────────────────────────────────────────

    @GetMapping("/consultations/{id}/prescription-pdf")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Download prescription PDF for a consultation")
    public ResponseEntity<byte[]> generatePrescriptionPdf(@PathVariable UUID id) {
        byte[] pdf = clinicPdfService.generatePrescription(
                TenantContext.getTenantIdAsObject(), id);
        return pdfResponse(pdf, "prescription-" + id + ".pdf");
    }

    // ── Medication Catalogue ──────────────────────────────────────────────────

    @GetMapping("/medications")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Search NAPPI medication catalogue — used for prescription autocomplete")
    public ResponseEntity<ApiResponse<List<ClinicMedicationCatalogue>>> searchMedications(
            @RequestParam(required = false, defaultValue = "") String search) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var results = search.isBlank()
                ? medicationRepo.findAll(tenantId)
                : medicationRepo.search(tenantId, search);
        return ResponseEntity.ok(ApiResponse.success("Success", results));
    }

    // ── PDF helper ────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // ── FIX #7 — Procedure catalogue endpoint ─────────────────────────────────
    // ClaimsTab tariff dropdown was hardcoded. Now fetches from clinic_procedure_catalogue
    // which is seeded from the NRPL gazette (V79). Rate changes only need a DB update.

    @GetMapping("/procedures")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Search NRPL procedure tariff catalogue")
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.clinic.domain.model.ClinicProcedureCatalogue>>> getProcedures(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String specialty) {
        var results = search != null && !search.isBlank()
                ? procedureRepo.search(search)
                : specialty != null
                ? procedureRepo.findBySpecialty(specialty)
                : procedureRepo.findAllActive();
        return ResponseEntity.ok(ApiResponse.success("Success", results));
    }


}