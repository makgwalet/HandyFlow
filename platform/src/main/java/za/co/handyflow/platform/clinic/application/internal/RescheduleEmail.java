package za.co.handyflow.platform.clinic.application.internal;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Wording of the email a patient gets when their appointment is moved. Pure, so it can be tested without a mail server. */
final class RescheduleEmail {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    record Message(String subject, String html) {}

    private RescheduleEmail() {}

    /** True when the patient needs to hear about it: the time or the practitioner changed. */
    static boolean worthSending(java.time.Instant oldTime, java.util.UUID oldPractitioner, java.time.Instant newTime, java.util.UUID newPractitioner) {
        return !oldTime.equals(newTime) || !java.util.Objects.equals(oldPractitioner, newPractitioner);
    }

    static Message build(String firstName, String companyName, String practitionerName,
                         java.time.Instant oldTime, java.time.Instant newTime, ZoneId zone) {
        ZonedDateTime from = oldTime.atZone(zone);
        ZonedDateTime to = newTime.atZone(zone);
        String who = firstName == null || firstName.isBlank() ? "there" : firstName;
        String at = companyName == null || companyName.isBlank() ? "" : " at " + escape(companyName);
        String html = "<p>Dear " + escape(who) + ",</p>"
                + "<p>Your appointment" + at + " has been moved.</p>"
                + "<p><b>Was:</b> " + from.format(DATE) + ", " + from.format(TIME) + "<br/>"
                + "<b>Now:</b> " + to.format(DATE) + ", " + to.format(TIME) + "<br/>"
                + (practitionerName == null || practitionerName.isBlank() ? "" : "<b>Practitioner:</b> " + escape(practitionerName) + "<br/>")
                + "</p>"
                + "<p>If the new time does not suit you, please contact us as soon as possible.</p>";
        return new Message("Appointment moved — now " + to.format(DATE) + ", " + to.format(TIME), html);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
