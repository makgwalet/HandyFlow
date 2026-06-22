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

import java.time.Instant;           // FIX #7 — was missing
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // FIX #4 — removed the old 3-param getPatients overload.
    // Controller now always calls the 5-param version below.

    @Transactional(readOnly = true)
    public Page<PatientResponse> getPatients(
            TenantId tenantId, String search, UUID principalId,
            boolean includeArchived, Pageable pageable) {

        Page<ClinicPatient> page;
        if (principalId != null) {
            page = patientRepo.findByTenantIdAndPrincipalId(tenantId, principalId, pageable);
        } else if (search != null && !search.isBlank()) {
            page = includeArchived
                    ? patientRepo.searchIncludingArchived(tenantId, search.trim(), pageable)
                    : patientRepo.search(tenantId, search.trim(), pageable);
        } else {
            page = includeArchived
                    ? patientRepo.findByTenantId(tenantId, pageable)
                    : patientRepo.findActiveByTenantId(tenantId, pageable);
        }

        // Batch-load principal names to avoid N+1 on the patient list
        Set<UUID> principalIds = page.getContent().stream()
                .map(ClinicPatient::getPrincipalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> principalMap = principalIds.isEmpty()
                ? Collections.emptyMap()
                : patientRepo.findAllByIds(tenantId, principalIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        ClinicPatient::getFullName));

        return page.map(p -> toPatientResponse(p, principalMap));
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(TenantId tenantId, UUID id) {
        return patientRepo.findActiveById(tenantId, id)
                // FIX #6 — use enriched 2-arg mapper so UUID is correct
                .map(p -> toPatientResponse(p, Collections.emptyMap()))
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id.toString()));
    }

    @Transactional
    public PatientResponse createPatient(TenantId tenantId, CreatePatientRequest req) {
        ClinicPatient patient = ClinicPatient.create(
                tenantId,
                req.firstName(), req.lastName(),
                req.idNumber(), req.dateOfBirth(), req.gender(),
                req.phone(), req.email(),
                req.emergencyContactName(), req.emergencyContactPhone()
        );
        // Set family account fields if provided
        if (req.accountType() != null)   patient.setAccountType(req.accountType());
        if (req.principalId() != null)   patient.setPrincipalId(req.principalId());
        if (req.relationship() != null)  patient.setRelationship(req.relationship());

        patientRepo.save(patient);
        log.info("Created patient={} tenant={}", patient.getId(), tenantId);
        // FIX #6 — use enriched 2-arg mapper
        return toPatientResponse(patient, Collections.emptyMap());
    }

    @Transactional
    public PatientResponse patchPatient(TenantId tenantId, UUID id, Map<String, Object> updates) {
        var patient = patientRepo.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id.toString()));

        if (updates.containsKey("active"))
            patient.setActive((Boolean) updates.get("active"));
        if (updates.containsKey("accountType"))
            patient.setAccountType((String) updates.get("accountType"));
        if (updates.containsKey("principalId")) {
            var pidStr = updates.get("principalId");
            patient.setPrincipalId(pidStr != null ? UUID.fromString(pidStr.toString()) : null);
        }
        if (updates.containsKey("relationship"))
            patient.setRelationship((String) updates.get("relationship"));
        if (updates.containsKey("archivedAt") && updates.get("archivedAt") != null)
            patient.setArchivedAt(Instant.parse(updates.get("archivedAt").toString()));
        if (updates.containsKey("archiveReason"))
            patient.setArchiveReason((String) updates.get("archiveReason"));
        if (updates.containsKey("lastVisitAt") && updates.get("lastVisitAt") != null)
            patient.setLastVisitAt(Instant.parse(updates.get("lastVisitAt").toString()));

        var saved = patientRepo.save(patient);
        return toPatientResponse(saved, Collections.emptyMap());
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> getFamilyMembers(TenantId tenantId, UUID patientId) {
        var patient = patientRepo.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        String accountType = patient.getAccountType() != null ? patient.getAccountType() : "INDIVIDUAL";
        UUID rootPrincipalId = switch (accountType) {
            case "PRINCIPAL" -> patient.getId();
            case "DEPENDANT" -> patient.getPrincipalId();
            default          -> null;
        };

        if (rootPrincipalId == null) return Collections.emptyList();

        var members = new ArrayList<ClinicPatient>();
        if ("DEPENDANT".equals(accountType))
            patientRepo.findByTenantIdAndId(tenantId, rootPrincipalId).ifPresent(members::add);
        members.addAll(patientRepo.findDependantsByPrincipalId(tenantId, rootPrincipalId));
        members.removeIf(m -> m.getId().equals(patientId));

        return members.stream()
                .map(m -> toPatientResponse(m, Collections.emptyMap()))
                .toList();
    }

    // ── Practitioners ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PractitionerResponse> getPractitioners(TenantId tenantId, Pageable pageable) {
        return practitionerRepo.findAllActive(tenantId, pageable).map(this::toPractitionerResponse);
    }

    @Transactional(readOnly = true)
    public List<PractitionerResponse> getPractitionersList(TenantId tenantId) {
        return practitionerRepo.findAllActiveList(tenantId)
                .stream().map(this::toPractitionerResponse).toList();
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
        return mapAppointmentsPage(page, tenantId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPatientAppointments(TenantId tenantId, UUID patientId) {
        return mapAppointmentsList(appointmentRepo.findByPatient(tenantId, patientId), tenantId);
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
        return toAppointmentResponse(appt,
                loadPatientNames(tenantId, List.of(appt)),
                loadPractitionerNames(tenantId, List.of(appt)));
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
        return toAppointmentResponse(appt,
                loadPatientNames(tenantId, List.of(appt)),
                loadPractitionerNames(tenantId, List.of(appt)));
    }

    // ── Consultations ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConsultationResponse> getPatientConsultations(TenantId tenantId, UUID patientId) {
        return mapConsultationsList(consultationRepo.findByPatient(tenantId, patientId), tenantId);
    }

    @Transactional
    public ConsultationResponse createConsultation(TenantId tenantId, UUID patientId,
                                                   CreateConsultationRequest req) {
        ClinicPatient patient = patientRepo.findActiveById(tenantId, patientId)
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

        if (req.appointmentId() != null) {
            appointmentRepo.findActiveById(tenantId, req.appointmentId())
                    .ifPresent(a -> {
                        if (a.isActive()) { a.complete(); appointmentRepo.save(a); }
                    });
        }

        consultationRepo.save(c);

        // Update denormalised lastVisitAt — avoids MAX() join on patient list
        patient.setLastVisitAt(Instant.now());
        patientRepo.save(patient);

        log.info("Created consultation={} patient={}", c.getId(), patientId);

        // FIX #5 — build name maps with UUID keys (not domain ID objects)
        Map<UUID, String> patientNames = Map.of(patientId,
                patient.getFirstName() + " " + patient.getLastName());
        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, patientNames, practNames);
    }


    // ── Tenant-wide consultation list (for billing consultation picker) ────────

    @Transactional(readOnly = true)
    public Page<ConsultationResponse> getConsultations(TenantId tenantId,
                                                       boolean unbilled,
                                                       Pageable pageable) {
        // findAllUnbilled requires V84 migration + repo update — fall back to findAllActive
        // when unbilled filter is requested, filter in memory until migration is applied.
        Page<ClinicConsultation> page = consultationRepo.findAllActive(tenantId, pageable);

        Set<UUID> patientIds = page.getContent().stream()
                .map(ClinicConsultation::getPatientId)
                .collect(Collectors.toSet());
        Set<UUID> practIds = page.getContent().stream()
                .map(ClinicConsultation::getPractitionerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getFirstName() + " " + p.getLastName()));
        Map<UUID, String> practNames = loadPractitionerNamesById(tenantId, practIds);

        Page<ConsultationResponse> result = page.map(c -> toConsultationResponse(c, patientNames, practNames));

        // In-memory unbilled filter — replace with findAllUnbilled() once repo is updated
        if (unbilled) {
            var filtered = result.getContent().stream()
                    .filter(r -> !r.billed())
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }
        return result;
    }

    // ── Edit a saved consultation ──────────────────────────────────────────────

    @Transactional
    public ConsultationResponse updateConsultation(TenantId tenantId, UUID id,
                                                   CreateConsultationRequest req) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));

        boolean hasVitals = req.weightKg() != null || req.heightCm() != null
                || req.bloodPressure() != null || req.pulseBpm() != null
                || req.temperatureC() != null  || req.oxygenSatPct() != null;
        if (hasVitals) {
            c.recordVitals(
                    req.weightKg()    != null ? req.weightKg()     : c.getWeightKg(),
                    req.heightCm()    != null ? req.heightCm()     : c.getHeightCm(),
                    req.bloodPressure()!= null? req.bloodPressure(): c.getBloodPressure(),
                    req.pulseBpm()    != null ? req.pulseBpm()     : c.getPulseBpm(),
                    req.temperatureC()!= null ? req.temperatureC() : c.getTemperatureC(),
                    req.oxygenSatPct()!= null ? req.oxygenSatPct(): c.getOxygenSatPct()
            );
        }

        boolean hasClinical = req.history() != null || req.examination() != null
                || req.diagnosis() != null || req.icd10Codes() != null
                || req.treatmentPlan() != null || req.followUpDays() != null
                || req.chiefComplaint() != null;
        if (hasClinical) {
            c.recordClinical(
                    req.history()      != null ? req.history()      : c.getHistory(),
                    req.examination()  != null ? req.examination()  : c.getExamination(),
                    req.diagnosis()    != null ? req.diagnosis()    : c.getDiagnosis(),
                    req.icd10Codes()   != null ? req.icd10Codes()   : c.getIcd10Codes(),
                    req.treatmentPlan()!= null ? req.treatmentPlan(): c.getTreatmentPlan(),
                    req.followUpDays() != null ? req.followUpDays() : c.getFollowUpDays()
            );
            if (req.chiefComplaint() != null) {
                c.updateChiefComplaint(req.chiefComplaint());
            }
        }

        consultationRepo.save(c);
        log.info("Updated consultation={}", id);

        Map<UUID, String> patientNames = patientRepo.findActiveById(tenantId, c.getPatientId())
                .map(p -> Map.of(c.getPatientId(), p.getFirstName() + " " + p.getLastName()))
                .orElse(Map.of());
        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, patientNames, practNames);
    }

    // ── Prescriptions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getConsultationPrescriptions(TenantId tenantId,
                                                                   UUID consultationId) {
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

    // ── N+1 fix helpers ───────────────────────────────────────────────────────

    private Page<AppointmentResponse> mapAppointmentsPage(Page<ClinicAppointment> page, TenantId tenantId) {
        Map<UUID, String> patientNames = loadPatientNames(tenantId, page.getContent());
        Map<UUID, String> practNames   = loadPractitionerNames(tenantId, page.getContent());
        return page.map(a -> toAppointmentResponse(a, patientNames, practNames));
    }

    private List<AppointmentResponse> mapAppointmentsList(List<ClinicAppointment> list, TenantId tenantId) {
        Map<UUID, String> patientNames = loadPatientNames(tenantId, list);
        Map<UUID, String> practNames   = loadPractitionerNames(tenantId, list);
        return list.stream().map(a -> toAppointmentResponse(a, patientNames, practNames)).toList();
    }

    private List<ConsultationResponse> mapConsultationsList(List<ClinicConsultation> list, TenantId tenantId) {
        Set<UUID> patientIds = list.stream().map(ClinicConsultation::getPatientId).collect(Collectors.toSet());
        Set<UUID> practIds   = list.stream().map(ClinicConsultation::getPractitionerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getFirstName() + " " + p.getLastName()));
        Map<UUID, String> practNames = practIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getFirstName() + " " + p.getLastName()));

        return list.stream().map(c -> toConsultationResponse(c, patientNames, practNames)).toList();
    }

    private Map<UUID, String> loadPatientNames(TenantId tenantId, List<ClinicAppointment> appts) {
        Set<UUID> ids = appts.stream().map(ClinicAppointment::getPatientId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return patientRepo.findAllByIds(tenantId, ids).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getFirstName() + " " + p.getLastName()));
    }

    private Map<UUID, String> loadPractitionerNames(TenantId tenantId, List<ClinicAppointment> appts) {
        Set<UUID> ids = appts.stream().map(ClinicAppointment::getPractitionerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return loadPractitionerNamesById(tenantId, ids);
    }

    private Map<UUID, String> loadPractitionerNamesById(TenantId tenantId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return practitionerRepo.findAllByIds(tenantId, ids).stream()
                .collect(Collectors.toMap(
                        p -> p.getId(),
                        p -> p.getFirstName() + " " + p.getLastName()));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    // FIX #6 — removed the old single-arg toPatientResponse which passed getId() (domain object)
    // as UUID. Only the enriched 2-arg version exists now; all call sites use it.
    private PatientResponse toPatientResponse(ClinicPatient p, Map<UUID, String> principalMap) {
        return new PatientResponse(
                p.getId(),                                              // FIX #6
                p.getFirstName(),
                p.getLastName(),
                p.getFullName() != null
                        ? p.getFullName()
                        : p.getFirstName() + " " + p.getLastName(),
                p.getIdNumber(),
                p.getDateOfBirth(),
                p.getGender(),
                p.getPhone(),
                p.getEmail(),
                p.getBloodType(),
                p.getAllergies(),
                p.getChronicConditions(),
                p.getEmergencyContactName(),
                p.getEmergencyContactPhone(),
                p.getNotes(),
                p.isActive(),
                p.getCreatedAt(),
                // P5 family fields
                p.getAccountType() != null ? p.getAccountType() : "INDIVIDUAL",
                p.getPrincipalId(),
                p.getPrincipalId() != null ? principalMap.get(p.getPrincipalId()) : null,
                p.getRelationship(),
                p.getLastVisitAt(),
                p.getArchivedAt()
        );
    }

    private PractitionerResponse toPractitionerResponse(ClinicPractitioner p) {
        return new PractitionerResponse(p.getId(), p.getFirstName(), p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getSpecialty(), p.getHpcsaNumber(), p.getPracticeNumber(),
                p.getPhone(), p.getEmail(), p.isActive(), p.getCreatedAt());
    }

    private AppointmentResponse toAppointmentResponse(ClinicAppointment a,
                                                      Map<UUID, String> patientNames,
                                                      Map<UUID, String> practNames) {
        return new AppointmentResponse(
                a.getId(), a.getPatientId(),
                patientNames.getOrDefault(a.getPatientId(), "Unknown"),
                a.getPractitionerId(),
                a.getPractitionerId() != null ? practNames.get(a.getPractitionerId()) : null,
                a.getScheduledAt(), a.getDurationMinutes(),
                a.getAppointmentType(), a.getStatus(), a.getReason(), a.getNotes(),
                a.getCreatedAt());
    }

    private ConsultationResponse toConsultationResponse(ClinicConsultation c,
                                                        Map<UUID, String> patientNames,
                                                        Map<UUID, String> practNames) {
        return new ConsultationResponse(
                c.getId(), c.getPatientId(),
                patientNames.getOrDefault(c.getPatientId(), "Unknown"),
                c.getPractitionerId(),
                c.getPractitionerId() != null ? practNames.get(c.getPractitionerId()) : null,
                c.getAppointmentId(), c.getConsultedAt(),
                c.getWeightKg(), c.getHeightCm(), c.getBloodPressure(), c.getPulseBpm(),
                c.getTemperatureC(), c.getOxygenSatPct(), c.getChiefComplaint(),
                c.getHistory(), c.getExamination(), c.getDiagnosis(), c.getIcd10Codes(),
                c.getTreatmentPlan(), c.getFollowUpDays(), c.isBilled(), c.getBillingAmount(),
                c.getCreatedAt());
    }

    private PrescriptionResponse toPrescriptionResponse(ClinicPrescription p) {
        return new PrescriptionResponse(p.getId(), p.getConsultationId(), p.getPatientId(),
                p.getMedicationName(), p.getDosage(), p.getFrequency(), p.getDuration(),
                p.getQuantity(), p.getRepeats(), p.getInstructions(),
                p.isDispensed(), p.getPrescribedAt());
    }
}