package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicAppointmentRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * FIX: "no appointment reminders" gap — the audit's own framing: "likely
 * the single biggest revenue-protecting feature missing from this module,
 * given no-show reduction is where most clinic software earns its keep."
 * <p>
 * Shared by both ClinicAppointmentReminderScheduler (nightly automatic
 * sweep) and the manual "Send reminder" action in the Schedule tab — one
 * send path, not two.
 * <p>
 * Resolves the tenant's display name via a direct JDBC query rather than
 * TenantFacade — this service runs from a cross-tenant scheduler context
 * where ClinicAppointment only stores a raw tenant UUID (not the wrapped
 * TenantId type Clinic entities generally carry), and constructing a
 * TenantId from that raw value isn't a pattern seen elsewhere in this
 * codebase. Same jdbc.queryForObject("SELECT name FROM tenants WHERE id
 * = ?", ...) approach already confirmed working for PDF headers in the
 * Fuel module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicAppointmentReminderService {

    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /**
     * Sends (or re-sends, for the manual trigger) a reminder for one
     * appointment. Mark-before-send: a send failure should not cause a
     * retry loop on the next scheduler run — an occasional missed
     * automatic reminder is preferable to double-sending or looping on a
     * bad email address. The manual trigger deliberately does not check
     * reminderSentAt first — staff may want to re-send even after the
     * automatic one already fired (e.g. "patient says they never got it").
     */
    @Transactional
    public void sendReminder(UUID appointmentId) {
        ClinicAppointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId.toString()));

        appt.markReminderSent();
        appointmentRepo.save(appt);

        try {
            ClinicPatient patient = patientRepo.findById(appt.getPatientId()).orElse(null);
            if (patient == null || patient.getEmail() == null || patient.getEmail().isBlank()) {
                log.info("No email on file for patient={} — reminder not sent for appointment={}",
                        appt.getPatientId(), appt.getId());
                return;
            }

            ClinicPractitioner practitioner = appt.getPractitionerId() != null
                    ? practitionerRepo.findById(appt.getPractitionerId()).orElse(null)
                    : null;
            String companyName = resolveTenantName(appt.getTenantId());

            ZonedDateTime zdt = appt.getScheduledAt().atZone(ZoneId.of("Africa/Johannesburg"));
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";

            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>This is a reminder of your upcoming appointment"
                    + (companyName != null ? " at " + companyName : "") + ":</p>"
                    + "<p><b>Date:</b> " + zdt.format(DATE_FMT) + "<br/>"
                    + "<b>Time:</b> " + zdt.format(TIME_FMT) + "<br/>"
                    + (practitioner != null ? "<b>Practitioner:</b> " + drName(practitioner.getFullName()) + "<br/>" : "")
                    + (appt.getAppointmentType() != null ? "<b>Type:</b> " + appt.getAppointmentType().replace("_", " ") + "<br/>" : "")
                    + "</p>"
                    + "<p>If you need to reschedule or cancel, please contact us as soon as possible.</p>";

            emailService.send(patient.getEmail(), "Appointment reminder — " + zdt.format(DATE_FMT), html);
            log.info("Sent appointment reminder patient={} appointment={} scheduledAt={}",
                    patient.getId(), appt.getId(), appt.getScheduledAt());
        } catch (Exception e) {
            log.warn("Appointment reminder not sent for appointment={}: {}", appt.getId(), e.getMessage());
        }
    }

    private String resolveTenantName(UUID tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, tenantId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * FIX: confirmed via real testing — "Dr. " was unconditionally
     * prepended to practitioner names, producing "Dr. Dr Sarah Mokoena"
     * for practitioners whose stored fullName already includes "Dr" (a
     * pre-existing data inconsistency — other practitioners' names don't
     * have it). Same fix applied everywhere this codebase builds a
     * "Dr. {name}" display string.
     */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("dr")
                ? trimmed : "Dr. " + trimmed;
    }
}