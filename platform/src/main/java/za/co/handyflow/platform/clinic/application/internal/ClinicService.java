package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;           // FIX #7 — was missing
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
    private final ClinicConsultationEditRepository consultationEditRepo;
    private final ClinicPatientClinicalService     patientClinicalService;
    private final ClinicObservationService         observationService;
    private final ClinicPatientIdentityService     patientIdentityService;
    private final ClinicQuestionLibraryService     questionLibraryService;
    private final ClinicPrescriptionRepository prescriptionRepo;
    private final ClinicPrescribingSafetyService prescribingSafety;
    private final EmailService                 emailService;
    private final ClinicConsultationSummaryPdfService consultationSummaryPdfService;
    private final JdbcTemplate                 jdbc;

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
        // S1-7: structure check, DOB agreement, and exact-ID duplicate block.
        patientIdentityService.assertCanRegister(tenantId, req.idNumber(), req.dateOfBirth());
        java.time.LocalDate dob = req.dateOfBirth() != null ? req.dateOfBirth()
                : Optional.ofNullable(SaIdNumber.parseOrNull(req.idNumber())).map(SaIdNumber::dateOfBirth).orElse(null);
        ClinicPatient patient = ClinicPatient.create(
                tenantId,
                req.firstName(), req.lastName(),
                req.idNumber(), dob, req.gender(),
                req.phone(), req.email(),
                req.emergencyContactName(), req.emergencyContactPhone()
        );
        // Set family account fields if provided
        if (req.accountType() != null)   patient.setAccountType(req.accountType());
        if (req.principalId() != null)   patient.setPrincipalId(req.principalId());
        if (req.relationship() != null)  patient.setRelationship(req.relationship());

        patient.setPatientNumber(patientIdentityService.nextPatientNumber(tenantId));
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
            UUID newPrincipal;
            try {
                newPrincipal = pidStr != null ? UUID.fromString(pidStr.toString()) : null;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("principalId is not a valid id");
            }
            validatePrincipal(tenantId, id, newPrincipal);
            patient.setPrincipalId(newPrincipal);
        }
        if (updates.containsKey("relationship"))
            patient.setRelationship((String) updates.get("relationship"));
        if (updates.containsKey("archivedAt") && updates.get("archivedAt") != null)
            patient.setArchivedAt(Instant.parse(updates.get("archivedAt").toString()));
        if (updates.containsKey("archiveReason"))
            patient.setArchiveReason((String) updates.get("archiveReason"));
        if (updates.containsKey("lastVisitAt") && updates.get("lastVisitAt") != null)
            patient.setLastVisitAt(Instant.parse(updates.get("lastVisitAt").toString()));

        // Reproductive context. sex_at_birth drives sex-specific question visibility, not gender.
        if (updates.containsKey("sexAtBirth")) {
            patient.setSexAtBirth(enumOrNull(updates.get("sexAtBirth"), "sexAtBirth",
                    Set.of("MALE", "FEMALE", "INTERSEX", "UNKNOWN")));
        }
        if (updates.containsKey("pregnancyStatus")) {
            patient.setPregnancyStatus(enumOrNull(updates.get("pregnancyStatus"), "pregnancyStatus",
                    Set.of("NOT_PREGNANT", "PREGNANT", "UNKNOWN")));
        }
        if (updates.containsKey("expectedDeliveryDate")) {
            Object v = updates.get("expectedDeliveryDate");
            try {
                patient.setExpectedDeliveryDate(v == null || v.toString().isBlank()
                        ? null : java.time.LocalDate.parse(v.toString().trim()));
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("expectedDeliveryDate must be an ISO date (yyyy-MM-dd)");
            }
        }

        // Clinical / contact fields (audit F-01). Absent key = unchanged; an empty list clears.
        if (updates.containsKey("allergies") || updates.containsKey("chronicConditions")
                || updates.containsKey("bloodType") || updates.containsKey("notes")
                || updates.containsKey("phone") || updates.containsKey("email")
                || updates.containsKey("emergencyContactName")
                || updates.containsKey("emergencyContactPhone")) {
            patient.update(
                    optString(updates, "phone"), optString(updates, "email"),
                    optString(updates, "emergencyContactName"),
                    optString(updates, "emergencyContactPhone"),
                    optString(updates, "bloodType"),
                    optStringList(updates, "allergies"),
                    optStringList(updates, "chronicConditions"),
                    optString(updates, "notes"));
        }

        var saved = patientRepo.save(patient);
        // Keep the structured allergy/condition rows (V339) in step with the plain lists.
        if (updates.containsKey("allergies") || updates.containsKey("chronicConditions")) {
            patientClinicalService.syncFromLegacyLists(tenantId, saved,
                    optStringList(updates, "allergies"), optStringList(updates, "chronicConditions"));
        }
        return toPatientResponse(saved, Collections.emptyMap());
    }

    /** Null/blank clears; otherwise upper-cased and checked against the allowed values. */
    private static String enumOrNull(Object v, String field, Set<String> allowed) {
        if (v == null || v.toString().isBlank()) return null;
        String u = v.toString().trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(u)) {
            throw new IllegalArgumentException(field + " must be one of " + new TreeSet<>(allowed));
        }
        return u;
    }

    private static String optString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString().trim();
    }

    /** Null when the key is absent or null (leave unchanged); otherwise a cleaned list. */
    private static List<String> optStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (!(v instanceof Collection<?> c)) {
            throw new IllegalArgumentException(key + " must be a list of strings");
        }
        List<String> out = new ArrayList<>();
        for (Object o : c) {
            if (o == null) continue;
            String t = o.toString().trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** principalId must exist in this tenant, not be the patient itself, and not be a dependant itself. */
    private void validatePrincipal(TenantId tenantId, UUID patientId, UUID principalId) {
        if (principalId == null) return;
        if (principalId.equals(patientId)) {
            throw new IllegalArgumentException("A patient cannot be their own principal");
        }
        var principal = patientRepo.findByTenantIdAndId(tenantId, principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Principal patient", principalId.toString()));
        if (principal.getPrincipalId() != null) {
            throw new IllegalArgumentException("The chosen principal is itself a dependant");
        }
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
        ClinicPatient patient = patientRepo.findActiveById(tenantId, req.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", req.patientId().toString()));
        // FIX: no validation existed on scheduledAt at all — an appointment
        // could be booked for any point in the past. Confirmed via real
        // testing that the frontend calendar picker's min= restriction is
        // UX only; nothing stopped a direct API call (or a bypassed
        // client) from submitting a past timestamp. This is the actual
        // enforcement; the frontend guard just avoids the round-trip for
        // the common case.
        if (req.scheduledAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Cannot book an appointment in the past");
        }
        ClinicAppointment appt = ClinicAppointment.create(
                tenantId, req.patientId(), req.practitionerId(),
                req.scheduledAt(),
                req.durationMinutes() != null ? req.durationMinutes() : 30,
                req.appointmentType(), req.reason()
        );
        appointmentRepo.save(appt);
        log.info("Created appointment={} patient={}", appt.getId(), req.patientId());

        // FIX: "no booking confirmation email" gap — createAppointment() had
        // zero email calls; the only patient-facing appointment email in the
        // whole module was the reminder, which fires later (nightly, or up
        // to ~28h ahead) — a patient who books got no confirmation of what
        // they'd just booked at all until then, if it arrived before the
        // appointment.
        sendBookingConfirmation(tenantId, appt, patient);

        return toAppointmentResponse(appt,
                loadPatientNames(tenantId, List.of(appt)),
                loadPractitionerNames(tenantId, List.of(appt)));
    }

    private void sendBookingConfirmation(TenantId tenantId, ClinicAppointment appt, ClinicPatient patient) {
        try {
            if (patient.getEmail() == null || patient.getEmail().isBlank()) {
                return;
            }
            ClinicPractitioner practitioner = appt.getPractitionerId() != null
                    ? practitionerRepo.findActiveById(tenantId, appt.getPractitionerId()).orElse(null)
                    : null;
            String companyName = resolveTenantName(appt.getTenantId());

            ZonedDateTime zdt = appt.getScheduledAt().atZone(ZoneId.of("Africa/Johannesburg"));
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";

            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>Your appointment" + (companyName != null && !companyName.isBlank() ? " at " + companyName : "") + " has been booked:</p>"
                    + "<p><b>Date:</b> " + zdt.format(dateFmt) + "<br/>"
                    + "<b>Time:</b> " + zdt.format(timeFmt) + "<br/>"
                    + (practitioner != null ? "<b>Practitioner:</b> " + drName(practitioner.getFullName()) + "<br/>" : "")
                    + (appt.getAppointmentType() != null ? "<b>Type:</b> " + appt.getAppointmentType().replace("_", " ") + "<br/>" : "")
                    + (appt.getReason() != null && !appt.getReason().isBlank() ? "<b>Reason:</b> " + appt.getReason() + "<br/>" : "")
                    + "</p>"
                    + "<p>If you need to reschedule or cancel, please contact us as soon as possible.</p>";

            emailService.send(patient.getEmail(),
                    "Appointment confirmed — " + zdt.format(dateFmt), html);
            log.info("Sent booking confirmation patient={} appointment={}", patient.getId(), appt.getId());
        } catch (Exception e) {
            log.warn("Booking confirmation not sent for appointment={}: {}", appt.getId(), e.getMessage());
        }
    }

    /**
     * DEC-CLINIC-002: signing a consultation does NOT email the visit summary. Email is a
     * deliberate, consent-gated delivery action (not built yet), so this stays off unless
     * explicitly enabled with handyflow.clinic.visit-summary-email.enabled=true.
     */
    @org.springframework.beans.factory.annotation.Value("${handyflow.clinic.visit-summary-email.enabled:false}")
    private boolean visitSummaryEmailEnabled;

    private void sendVisitSummaryEmail(TenantId tenantId, ClinicConsultation c, ClinicPatient patient) {
        if (!visitSummaryEmailEnabled) {
            log.debug("Visit summary email disabled (DEC-CLINIC-002); consultation={}", c.getId());
            return;
        }
        try {
            if (patient.getEmail() == null || patient.getEmail().isBlank()) {
                return;
            }
            byte[] pdfBytes = consultationSummaryPdfService.generate(tenantId, c.getId());
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>Thank you for your visit. A summary of today's consultation is attached for your records.</p>"
                    + "<p>If you have any questions, please contact the practice.</p>";
            emailService.sendWithAttachment(patient.getEmail(), "Your visit summary", html,
                    "visit-summary-" + c.getId() + ".pdf", pdfBytes);
            log.info("Sent visit summary patient={} consultation={}", patient.getId(), c.getId());
        } catch (Exception e) {
            log.warn("Visit summary not sent for consultation={}: {}", c.getId(), e.getMessage());
        }
    }

    /** Same jdbc.queryForObject pattern already confirmed working elsewhere in this module (ClinicAppointmentReminderService). */
    private String resolveTenantName(UUID tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, tenantId);
        } catch (Exception e) {
            return null;
        }
    }

    /** FIX: confirmed via real testing — see ClinicReferralPdfService for the full explanation. */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("dr") ? trimmed : "Dr. " + trimmed;
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(TenantId tenantId, UUID id, String action) {
        ClinicAppointment appt = appointmentRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));
        switch (action.toUpperCase().replace('-', '_')) {
            case "CONFIRM"  -> appt.confirm();
            case "CHECK_IN" -> appt.checkIn();
            case "TRIAGE"   -> appt.triage();
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
        observationService.syncConsultationVitals(tenantId, c);

        // Update denormalised lastVisitAt — avoids MAX() join on patient list
        patient.setLastVisitAt(Instant.now());
        patientRepo.save(patient);

        log.info("Created consultation={} patient={}", c.getId(), patientId);

        // FIX: "no PDF is ever emailed" gap — visit summary was
        // download-only. Deliberately fires right after the consultation
        // is recorded, not on some later "finalize" step — this codebase
        // has no separate draft/final state for a consultation, so
        // "recorded" is the only real completion signal available.
        sendVisitSummaryEmail(tenantId, c, patient);

        // FIX #5 — build name maps with UUID keys (not domain ID objects)
        Map<UUID, String> patientNames = Map.of(patientId,
                patient.getFirstName() + " " + patient.getLastName());
        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, patientNames, practNames);
    }


    // ── Tenant-wide consultation list (for billing consultation picker) ────────

    /**
     * FIX: backlog 13.2 — findAllUnbilled() already existed, fully
     * correct, using the existing `billed` column — no migration was
     * ever actually needed. Filtering at the query level also fixes a
     * real bug the old in-memory version had: it paginated on the FULL
     * active set first, then filtered — silently dropping any unbilled
     * consultation outside that page's window even when there was room
     * within the same page size counting unbilled ones alone.
     */
    @Transactional(readOnly = true)
    public Page<ConsultationResponse> getConsultations(TenantId tenantId,
                                                       boolean unbilled,
                                                       Pageable pageable) {
        Page<ClinicConsultation> page = unbilled
                ? consultationRepo.findAllUnbilled(tenantId, pageable)
                : consultationRepo.findAllActive(tenantId, pageable);

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

        return page.map(c -> toConsultationResponse(c, patientNames, practNames));
    }

    // ── Drafts (autosave target) ───────────────────────────────────────────────

    /**
     * Starts a persisted DRAFT consultation. Unlike {@link #createConsultation} this
     * deliberately does NOT complete the appointment, touch lastVisitAt or email the
     * visit summary: those happen on {@link #signConsultation}.
     */
    @Transactional
    public ConsultationResponse createDraftConsultation(TenantId tenantId, UUID patientId,
                                                        CreateConsultationRequest req) {
        ClinicPatient patient = patientRepo.findActiveById(tenantId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        if (req.appointmentId() != null
                && !consultationRepo.findUnsignedByAppointment(tenantId, req.appointmentId()).isEmpty()) {
            throw new IllegalStateException(
                    "This appointment already has an open consultation (possibly handed over to a doctor).");
        }
        ClinicConsultation c = ClinicConsultation.createDraft(
                tenantId, patientId, req.appointmentId(), req.practitionerId(), req.chiefComplaint());
        c.startedBy(currentUserIdOrNull());
        c.recordVitals(req.weightKg(), req.heightCm(), req.bloodPressure(),
                req.pulseBpm(), req.temperatureC(), req.oxygenSatPct());
        c.recordClinical(req.history(), req.examination(), req.diagnosis(),
                req.icd10Codes(), req.treatmentPlan(), req.followUpDays());
        consultationRepo.save(c);
        log.info("Created DRAFT consultation={} patient={}", c.getId(), patientId);

        Map<UUID, String> patientNames = Map.of(patientId,
                patient.getFirstName() + " " + patient.getLastName());
        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, patientNames, practNames);
    }

    @Transactional(readOnly = true)
    public List<ConsultationResponse> getDraftConsultations(TenantId tenantId) {
        return getDraftConsultations(tenantId, false);
    }

    /** With {@code mine}, only drafts this user started; drafts from before authors were recorded are not "mine". */
    public List<ConsultationResponse> getDraftConsultations(TenantId tenantId, boolean mine) {
        List<ClinicConsultation> drafts = consultationRepo.findDrafts(tenantId);
        if (mine) {
            UUID me = currentUserIdOrNull();
            drafts = drafts.stream().filter(d -> me != null && me.equals(d.getCreatedBy())).toList();
        }
        return mapConsultationsList(drafts, tenantId);
    }


    /** DRAFT -> SIGNED. Completes the appointment, stamps lastVisitAt, emails the summary. */
    @Transactional
    public ConsultationResponse signConsultation(TenantId tenantId, UUID id) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));
        if (!c.isSignable()) {
            throw new IllegalStateException("Only a DRAFT consultation, or one the doctor has completed, can be signed (is "
                    + c.getStatus() + ").");
        }
        List<String> unfinished = questionLibraryService.incompleteGroups(tenantId, c);
        if (!unfinished.isEmpty()) {
            throw new IllegalStateException("Finish the questionnaire before signing. Missing: " + String.join("; ", unfinished) + ".");
        }
        c.sign();
        consultationRepo.save(c);
        observationService.syncConsultationVitals(tenantId, c);

        if (c.getAppointmentId() != null) {
            appointmentRepo.findActiveById(tenantId, c.getAppointmentId())
                    .ifPresent(a -> { if (a.isActive()) { a.complete(); appointmentRepo.save(a); } });
        }
        ClinicPatient patient = patientRepo.findActiveById(tenantId, c.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", c.getPatientId().toString()));
        patient.setLastVisitAt(Instant.now());
        patientRepo.save(patient);
        sendVisitSummaryEmail(tenantId, c, patient);
        log.info("Signed consultation={}", id);

        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, Map.of(c.getPatientId(),
                patient.getFirstName() + " " + patient.getLastName()), practNames);
    }

    /** DRAFT -> ABANDONED (kept for audit, hidden from the drafts tray and billing). */
    @Transactional
    public void abandonConsultation(TenantId tenantId, UUID id) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));
        if (!c.isAbandonable()) {
            throw new IllegalStateException("This consultation cannot be abandoned from " + c.getStatus() + ".");
        }
        c.abandon();
        consultationRepo.save(c);
    }

    // ── Edit a saved consultation ──────────────────────────────────────────────

    @Transactional
    public ConsultationResponse updateConsultation(TenantId tenantId, UUID id,
                                                   CreateConsultationRequest req) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));
        if (c.isLocked()) {
            throw new IllegalStateException("Consultation is " + c.getStatus()
                    + " and can no longer be edited; add an addendum instead.");
        }
        if (c.isAwaitingHandoff()) {
            throw new IllegalStateException("Consultation is " + c.getStatus()
                    + ": it is with the other clinician. Accept it, or return it to the nurse, before editing.");
        }

        // Edits to an already-signed record keep the previous version (audit F-02).
        // DRAFT autosaves are not recorded: they are the working copy, not the record.
        if (c.isSigned()) {
            consultationEditRepo.save(ClinicConsultationEdit.snapshotOf(c, currentUserIdOrNull()));
        }

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
        if (c.isSigned()) observationService.syncConsultationVitals(tenantId, c);
        log.info("Updated consultation={}", id);

        Map<UUID, String> patientNames = patientRepo.findActiveById(tenantId, c.getPatientId())
                .map(p -> Map.of(c.getPatientId(), p.getFirstName() + " " + p.getLastName()))
                .orElse(Map.of());
        Map<UUID, String> practNames = c.getPractitionerId() != null
                ? loadPractitionerNamesById(tenantId, List.of(c.getPractitionerId()))
                : Map.of();
        return toConsultationResponse(c, patientNames, practNames);
    }

    @Transactional(readOnly = true)
    public List<ConsultationEditResponse> getConsultationEdits(TenantId tenantId, UUID consultationId) {
        consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        return consultationEditRepo.findByConsultation(tenantId, consultationId).stream()
                .map(e -> new ConsultationEditResponse(e.getId(), e.getConsultationId(), e.getEditedBy(),
                        e.getEditedAt(), e.getChiefComplaint(), e.getHistory(), e.getExamination(),
                        e.getDiagnosis(), e.getIcd10Codes(), e.getTreatmentPlan(), e.getFollowUpDays(),
                        e.getWeightKg(), e.getHeightCm(), e.getBloodPressure(), e.getPulseBpm(),
                        e.getTemperatureC(), e.getOxygenSatPct()))
                .toList();
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
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
                req.quantity(), req.repeats() != null ? req.repeats() : 0, req.instructions(),
                req.nappiCode(), req.schedule()
        );
        var alerts = prescribingSafety.allergyAlerts(tenantId, c.getPatientId(), req.medicationName());
        if (!alerts.isEmpty()) {
            String summary = ClinicPrescribingSafetyService.summary(alerts);
            String reason = req.allergyOverrideReason() == null ? "" : req.allergyOverrideReason().trim();
            if (reason.isEmpty()) {
                throw new ConflictException("Recorded allergy matches this medicine: " + summary
                        + ". Give a reason to prescribe it anyway.");
            }
            p.recordAllergyOverride(reason, summary);
        }
        prescriptionRepo.save(p);
        return toPrescriptionResponse(p);
    }

    /** What a medicine name matches among the patient's recorded allergies (a prompt, not a safety clearance). */
    @Transactional(readOnly = true)
    public AllergyCheckResponse checkAllergies(TenantId tenantId, UUID consultationId, String medicineName) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        return AllergyCheckResponse.of(prescribingSafety.allergyAlerts(tenantId, c.getPatientId(), medicineName));
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

    /** Maps consultations to responses (patient and practitioner names resolved in bulk). */
    @Transactional(readOnly = true)
    public List<ConsultationResponse> toResponses(TenantId tenantId, List<ClinicConsultation> list) {
        return mapConsultationsList(list, tenantId);
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
                p.getArchivedAt(),
                p.getSexAtBirth(),
                p.getPregnancyStatus(),
                p.getExpectedDeliveryDate(),
                p.getPatientNumber()
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
                c.getCreatedAt(), c.getStatus(), c.getUpdatedAt());
    }

    private PrescriptionResponse toPrescriptionResponse(ClinicPrescription p) {
        return new PrescriptionResponse(p.getId(), p.getConsultationId(), p.getPatientId(),
                p.getMedicationName(), p.getDosage(), p.getFrequency(), p.getDuration(),
                p.getQuantity(), p.getRepeats(), p.getInstructions(),
                p.isDispensed(), p.getPrescribedAt(), p.getNappiCode(), p.getSchedule(),
                p.getAllergyOverrideReason(), p.getAllergyAlertSummary());
    }
}