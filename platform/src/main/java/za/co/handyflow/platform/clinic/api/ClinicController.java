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
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientIdentityService;
import za.co.handyflow.platform.clinic.application.internal.ClinicPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicReferralPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicConsultationSummaryPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.application.internal.ClinicAppointmentReminderService;
import za.co.handyflow.platform.clinic.application.internal.ClinicTelehealthService;
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
    private final za.co.handyflow.platform.clinic.application.internal.ClinicDispensingService dispensingService;
    private final za.co.handyflow.platform.clinic.application.internal.ClinicAllergySnapshotService allergySnapshotService;
    private final ClinicPatientIdentityService       patientIdentityService;
    private final ClinicAppointmentReminderService    appointmentReminderService;
    private final ClinicTelehealthService              telehealthService;
    private final ClinicPdfService                   clinicPdfService;
    private final ClinicReferralPdfService            referralPdfService;
    private final ClinicConsultationSummaryPdfService consultationSummaryPdfService;
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

    @GetMapping("/patients/duplicate-check")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Possible existing patients (same ID number, or same name and date of birth) to show before creating one")
    public ResponseEntity<ApiResponse<List<ClinicPatientIdentityService.Candidate>>> duplicateCheck(
            @RequestParam(required = false) String idNumber,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateOfBirth) {
        return ResponseEntity.ok(ApiResponse.success("Success", patientIdentityService.findCandidates(
                TenantContext.getTenantIdAsObject(), idNumber, firstName, lastName, dateOfBirth)));
    }

    @GetMapping("/patients/id-number/validate")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Check an SA ID number; returns date of birth and sex digit when valid (a hint, not a record)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> validateIdNumber(@RequestParam String value) {
        String problem = za.co.handyflow.platform.clinic.domain.model.SaIdNumber.problem(value);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("valid", problem == null);
        out.put("applicable", za.co.handyflow.platform.clinic.domain.model.SaIdNumber.looksLikeSaId(value));
        if (problem != null) out.put("problem", problem);
        else {
            var id = za.co.handyflow.platform.clinic.domain.model.SaIdNumber.parseOrNull(value);
            out.put("dateOfBirth", id.dateOfBirth().toString());
            out.put("sexDigits", id.male() ? "MALE" : "FEMALE");
            out.put("citizen", id.citizen());
        }
        return ResponseEntity.ok(ApiResponse.success("Success", out));
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

    /**
     * FIX: "no appointment reminder UI" gap — direct consequence of the
     * "no appointment reminders" gap; ScheduleTab had booking and status
     * changes but no "send reminder" action. Shares the same send path as
     * the nightly ClinicAppointmentReminderScheduler.
     */
    @PostMapping("/appointments/{id}/send-reminder")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Manually send (or re-send) the appointment reminder email now")
    public ResponseEntity<ApiResponse<Void>> sendAppointmentReminder(@PathVariable UUID id) {
        appointmentReminderService.sendReminder(id);
        return ResponseEntity.ok(ApiResponse.success("Reminder sent", null));
    }

    /**
     * FIX: "no telehealth/video consultation option" gap. Called by either
     * party (staff or, via a future patient portal, the patient) on
     * joining — idempotent, so simultaneous calls from both sides land on
     * the same room.
     */
    @PostMapping("/appointments/{id}/video-room")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get or create the video call room for a telehealth appointment")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getVideoRoom(@PathVariable UUID id) {
        String url = telehealthService.getOrCreateVideoRoom(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Video room ready",
                java.util.Map.of("videoRoomUrl", url)));
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
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Record a consultation with vitals, clinical notes and diagnosis")
    public ResponseEntity<ApiResponse<ConsultationResponse>> createConsultation(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateConsultationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Consultation recorded",
                clinicService.createConsultation(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PatchMapping("/consultations/{id}")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Partial update of a consultation (autosave target); null fields are left unchanged")
    public ResponseEntity<ApiResponse<ConsultationResponse>> patchConsultation(
            @PathVariable UUID id,
            @RequestBody CreateConsultationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Consultation updated",
                clinicService.updateConsultation(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/patients/{patientId}/consultations/draft")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Start a persisted DRAFT consultation (no appointment completion, no email)")
    public ResponseEntity<ApiResponse<ConsultationResponse>> createDraftConsultation(
            @PathVariable UUID patientId,
            @RequestBody CreateConsultationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Draft started",
                clinicService.createDraftConsultation(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @GetMapping("/consultations/drafts")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Open DRAFT consultations for the tenant (drafts tray)")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getDraftConsultations(
            @RequestParam(defaultValue = "false") boolean mine) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getDraftConsultations(TenantContext.getTenantIdAsObject(), mine)));
    }

    @GetMapping("/consultations/{id}/edits")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Edit history of a consultation (previous versions, newest first)")
    public ResponseEntity<ApiResponse<List<ConsultationEditResponse>>> getConsultationEdits(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.getConsultationEdits(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/consultations/{id}/sign")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
    @Operation(summary = "Sign a DRAFT consultation: completes the appointment (no automatic email, see DEC-CLINIC-002)")
    public ResponseEntity<ApiResponse<ConsultationResponse>> signConsultation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Consultation signed",
                clinicService.signConsultation(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/consultations/{id}/abandon")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Abandon a DRAFT consultation")
    public ResponseEntity<ApiResponse<Void>> abandonConsultation(@PathVariable UUID id) {
        clinicService.abandonConsultation(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Draft abandoned", null));
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

    @PostMapping("/prescriptions/{id}/fills")
    @PreAuthorize("hasAuthority('CLINIC_PRESCRIPTION_WRITE')")
    @Operation(summary = "Record a fill (the original supply or one authorised repeat); refused when all fills are used")
    public ResponseEntity<ApiResponse<FillDtos.FillResponse>> recordFill(
            @PathVariable UUID id, @RequestBody(required = false) FillDtos.FillRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Fill recorded",
                dispensingService.recordFill(TenantContext.getTenantIdAsObject(), id, body)));
    }

    @GetMapping("/prescriptions/{id}/fills")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Fills recorded against a prescription")
    public ResponseEntity<ApiResponse<List<FillDtos.FillResponse>>> fills(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dispensingService.fills(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/consultations/{id}/allergy-snapshot")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Allergies on record when the consultation was signed (not captured for older consultations)")
    public ResponseEntity<ApiResponse<AllergySnapshotResponse>> allergySnapshot(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                allergySnapshotService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/consultations/{consultationId}/prescriptions/allergy-check")
    @PreAuthorize("hasAuthority('CLINIC_PRESCRIPTION_WRITE')")
    @Operation(summary = "Compare a medicine name with the patient's recorded allergies (name match only; a prompt, not clearance)")
    public ResponseEntity<ApiResponse<AllergyCheckResponse>> checkAllergies(
            @PathVariable UUID consultationId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.checkAllergies(TenantContext.getTenantIdAsObject(), consultationId, body.get("medicationName"))));
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
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
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

    // ── Referral Letter PDF ───────────────────────────────────────────────────
    // FIX: "no referral letter" gap — a very standard GP output with no
    // equivalent before this. Not backed by a persisted entity — same
    // convention as the medical certificate above (free-text params
    // supplied at generation time, nothing stored).

    @PostMapping("/consultations/{id}/referral-letter")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
    @Operation(summary = "Generate a referral letter PDF for a consultation")
    public ResponseEntity<byte[]> generateReferralLetter(
            @PathVariable UUID id,
            @RequestParam(required = false) String specialistName,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) String additionalNotes) {
        byte[] pdf = referralPdfService.generate(
                TenantContext.getTenantIdAsObject(), id, specialistName, specialty, reason, urgency, additionalNotes);
        return pdfResponse(pdf, "referral-letter-" + id + ".pdf");
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

    // ── Consultation Summary PDF ─────────────────────────────────────────────
    // FIX: "no consultation summary/after-visit note PDF" gap — distinct
    // from the medical certificate and prescription, a standard GP output
    // this codebase never generated despite capturing the underlying data.

    @GetMapping("/consultations/{id}/summary-pdf")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Download a visit/consultation summary PDF")
    public ResponseEntity<byte[]> generateConsultationSummaryPdf(@PathVariable UUID id) {
        byte[] pdf = consultationSummaryPdfService.generate(TenantContext.getTenantIdAsObject(), id);
        return pdfResponse(pdf, "visit-summary-" + id + ".pdf");
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