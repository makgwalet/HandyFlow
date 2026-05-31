package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicService {

    private final ClinicPatientRepository      patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final ClinicAppointmentRepository  appointmentRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPrescriptionRepository prescriptionRepo;

    // ── Patients ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PatientResponse> getPatients(TenantId tenantId, String search, Pageable pageable) {
        Page<ClinicPatient> page = (search != null && !search.isBlank())
                ? patientRepo.searchActive(tenantId, search, pageable)
                : patientRepo.findAllActive(tenantId, pageable);
        return page.map(this::toPatientResponse);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(TenantId tenantId, UUID id) {
        return patientRepo.findActiveById(tenantId, id)
                .map(this::toPatientResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id.toString()));
    }

    @Transactional
    public PatientResponse createPatient(TenantId tenantId, CreatePatientRequest req) {
        ClinicPatient patient = ClinicPatient.create(
                tenantId, req.firstName(), req.lastName(),
                req.idNumber(), req.dateOfBirth(), req.gender(),
                req.phone(), req.email()
        );
        if (req.emergencyContactName() != null)
            patient.update(req.phone(), req.email(),
                    req.emergencyContactName(), req.emergencyContactPhone(),
                    null, null, null, null);
        patientRepo.save(patient);
        log.info("Created patient={} tenant={}", patient.getId(), tenantId);
        return toPatientResponse(patient);
    }

    // ── Practitioners ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PractitionerResponse> getPractitioners(TenantId tenantId, Pageable pageable) {
        return practitionerRepo.findAllActive(tenantId, pageable).map(this::toPractitionerResponse);
    }

    @Transactional(readOnly = true)
    public List<PractitionerResponse> getPractitionersList(TenantId tenantId) {
        return practitionerRepo.findAllActiveList(tenantId).stream().map(this::toPractitionerResponse).toList();
    }

    @Transactional
    public PractitionerResponse createPractitioner(TenantId tenantId, CreatePractitionerRequest req) {
        ClinicPractitioner p = ClinicPractitioner.create(
                tenantId, req.firstName(), req.lastName(),
                req.specialty(), req.hpcsaNumber(), req.practiceNumber(),
                req.phone(), req.email()
        );
        practitionerRepo.save(p);
        log.info("Created practitioner={} tenant={}", p.getId(), tenantId);
        return toPractitionerResponse(p);
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointments(TenantId tenantId, String status, Pageable pageable) {
        Page<ClinicAppointment> page = (status != null && !status.isBlank())
                ? appointmentRepo.findAllActiveByStatus(tenantId, status, pageable)
                : appointmentRepo.findAllActive(tenantId, pageable);
        return page.map(a -> toAppointmentResponse(a, tenantId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPatientAppointments(TenantId tenantId, UUID patientId) {
        return appointmentRepo.findByPatient(tenantId, patientId)
                .stream().map(a -> toAppointmentResponse(a, tenantId)).toList();
    }

    @Transactional
    public AppointmentResponse createAppointment(TenantId tenantId, CreateAppointmentRequest req) {
        patientRepo.findActiveById(tenantId, req.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", req.patientId().toString()));
        ClinicAppointment appt = ClinicAppointment.create(
                tenantId, req.patientId(), req.practitionerId(),
                req.scheduledAt(),
                req.durationMinutes() != null ? req.durationMinutes() : 30,
                req.appointmentType(), req.reason()
        );
        appointmentRepo.save(appt);
        log.info("Created appointment={} patient={}", appt.getId(), req.patientId());
        return toAppointmentResponse(appt, tenantId);
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(TenantId tenantId, UUID id, String action) {
        ClinicAppointment appt = appointmentRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));
        switch (action.toUpperCase()) {
            case "CONFIRM"  -> appt.confirm();
            case "START"    -> appt.start();
            case "COMPLETE" -> appt.complete();
            case "CANCEL"   -> appt.cancel();
            case "NO_SHOW"  -> appt.noShow();
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        appointmentRepo.save(appt);
        return toAppointmentResponse(appt, tenantId);
    }

    // ── Consultations ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConsultationResponse> getPatientConsultations(TenantId tenantId, UUID patientId) {
        return consultationRepo.findByPatient(tenantId, patientId)
                .stream().map(c -> toConsultationResponse(c, tenantId)).toList();
    }

    @Transactional
    public ConsultationResponse createConsultation(TenantId tenantId, UUID patientId,
                                                   CreateConsultationRequest req) {
        patientRepo.findActiveById(tenantId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        ClinicConsultation c = ClinicConsultation.create(
                tenantId, patientId, req.appointmentId(),
                req.practitionerId(), req.chiefComplaint()
        );
        if (req.weightKg() != null || req.bloodPressure() != null)
            c.recordVitals(req.weightKg(), req.heightCm(), req.bloodPressure(),
                    req.pulseBpm(), req.temperatureC(), req.oxygenSatPct());
        if (req.diagnosis() != null || req.history() != null)
            c.recordClinical(req.history(), req.examination(), req.diagnosis(),
                    req.icd10Codes(), req.treatmentPlan(), req.followUpDays());
        if (req.appointmentId() != null)
            appointmentRepo.findActiveById(tenantId, req.appointmentId())
                    .ifPresent(a -> { a.complete(); appointmentRepo.save(a); });
        consultationRepo.save(c);
        log.info("Created consultation={} patient={}", c.getId(), patientId);
        return toConsultationResponse(c, tenantId);
    }

    // ── Prescriptions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getConsultationPrescriptions(TenantId tenantId, UUID consultationId) {
        return prescriptionRepo.findByConsultation(tenantId, consultationId)
                .stream().map(this::toPrescriptionResponse).toList();
    }

    @Transactional
    public PrescriptionResponse addPrescription(TenantId tenantId, UUID consultationId,
                                                AddPrescriptionRequest req) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        ClinicPrescription p = ClinicPrescription.create(
                tenantId, consultationId, c.getPatientId(), c.getPractitionerId(),
                req.medicationName(), req.dosage(), req.frequency(), req.duration(),
                req.quantity(), req.repeats() != null ? req.repeats() : 0, req.instructions()
        );
        prescriptionRepo.save(p);
        return toPrescriptionResponse(p);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PatientResponse toPatientResponse(ClinicPatient p) {
        String full = p.getFirstName() + " " + p.getLastName();
        return new PatientResponse(p.getId(), p.getFirstName(), p.getLastName(), full,
                p.getIdNumber(), p.getDateOfBirth(), p.getGender(), p.getPhone(), p.getEmail(),
                p.getBloodType(), p.getAllergies(), p.getChronicConditions(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(),
                p.getNotes(), p.isActive(), p.getCreatedAt());
    }

    private PractitionerResponse toPractitionerResponse(ClinicPractitioner p) {
        return new PractitionerResponse(p.getId(), p.getFirstName(), p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getSpecialty(), p.getHpcsaNumber(), p.getPracticeNumber(),
                p.getPhone(), p.getEmail(), p.isActive(), p.getCreatedAt());
    }

    private AppointmentResponse toAppointmentResponse(ClinicAppointment a, TenantId tenantId) {
        String patientName = patientRepo.findActiveById(tenantId, a.getPatientId())
                .map(p -> p.getFirstName() + " " + p.getLastName()).orElse("Unknown");
        String practName = a.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, a.getPractitionerId())
                .map(p -> p.getFirstName() + " " + p.getLastName()).orElse(null)
                : null;
        return new AppointmentResponse(a.getId(), a.getPatientId(), patientName,
                a.getPractitionerId(), practName, a.getScheduledAt(), a.getDurationMinutes(),
                a.getAppointmentType(), a.getStatus(), a.getReason(), a.getNotes(), a.getCreatedAt());
    }

    private ConsultationResponse toConsultationResponse(ClinicConsultation c, TenantId tenantId) {
        String patientName = patientRepo.findActiveById(tenantId, c.getPatientId())
                .map(p -> p.getFirstName() + " " + p.getLastName()).orElse("Unknown");
        String practName = c.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, c.getPractitionerId())
                .map(p -> p.getFirstName() + " " + p.getLastName()).orElse(null)
                : null;
        return new ConsultationResponse(c.getId(), c.getPatientId(), patientName,
                c.getPractitionerId(), practName, c.getAppointmentId(), c.getConsultedAt(),
                c.getWeightKg(), c.getHeightCm(), c.getBloodPressure(), c.getPulseBpm(),
                c.getTemperatureC(), c.getOxygenSatPct(), c.getChiefComplaint(),
                c.getHistory(), c.getExamination(), c.getDiagnosis(), c.getIcd10Codes(),
                c.getTreatmentPlan(), c.getFollowUpDays(), c.isBilled(), c.getBillingAmount(),
                c.getCreatedAt());
    }

    private PrescriptionResponse toPrescriptionResponse(ClinicPrescription p) {
        return new PrescriptionResponse(p.getId(), p.getConsultationId(), p.getPatientId(),
                p.getMedicationName(), p.getDosage(), p.getFrequency(), p.getDuration(),
                p.getQuantity(), p.getRepeats(), p.getInstructions(), p.isDispensed(), p.getPrescribedAt());
    }
}