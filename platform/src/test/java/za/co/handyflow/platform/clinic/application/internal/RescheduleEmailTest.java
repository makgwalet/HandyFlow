package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RescheduleEmailTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant OLD = Instant.parse("2026-10-07T07:00:00Z");   // Wed 09:00
    private static final Instant NEW = Instant.parse("2026-10-08T12:30:00Z");   // Thu 14:30

    @Test
    void sendsOnlyWhenTheTimeOrPractitionerChanged() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(RescheduleEmail.worthSending(OLD, a, OLD, a));
        assertFalse(RescheduleEmail.worthSending(OLD, null, OLD, null));
        assertTrue(RescheduleEmail.worthSending(OLD, a, NEW, a));
        assertTrue(RescheduleEmail.worthSending(OLD, a, OLD, b));
        assertTrue(RescheduleEmail.worthSending(OLD, null, OLD, b));
    }

    @Test
    void sayswhatChangedInClinicTime() {
        var m = RescheduleEmail.build("Jane", "Lee Family Practice", "Dr Lee", OLD, NEW, SAST);
        assertEquals("Appointment moved — now Thursday, 8 October 2026, 2:30 PM", m.subject());
        assertTrue(m.html().contains("<b>Was:</b> Wednesday, 7 October 2026, 9:00 AM"), m.html());
        assertTrue(m.html().contains("<b>Now:</b> Thursday, 8 October 2026, 2:30 PM"), m.html());
        assertTrue(m.html().contains("Dear Jane,"));
        assertTrue(m.html().contains(" at Lee Family Practice"));
        assertTrue(m.html().contains("<b>Practitioner:</b> Dr Lee"));
    }

    @Test
    void coversMissingNamesAndEscapesWhatItIsGiven() {
        var m = RescheduleEmail.build(null, null, null, OLD, NEW, SAST);
        assertTrue(m.html().contains("Dear there,"));
        assertFalse(m.html().contains("Practitioner"));
        var e = RescheduleEmail.build("<b>x</b>", "A & B", "Dr <i>", OLD, NEW, SAST);
        assertFalse(e.html().contains("<b>x</b>"));
        assertTrue(e.html().contains("&lt;b&gt;x&lt;/b&gt;"));
        assertTrue(e.html().contains("A &amp; B"));
        assertTrue(e.html().contains("Dr &lt;i&gt;"));
    }
}
