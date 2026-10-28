package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterEmailTemplatesTest {
    @Test
    void valuesFromPeopleAreEscaped() {
        assertThat(RecruiterEmailTemplates.esc("<b>\"x\" & 'y'</b>")).isEqualTo("&lt;b&gt;&quot;x&quot; &amp; &#39;y&#39;&lt;/b&gt;");
        assertThat(RecruiterEmailTemplates.esc(null)).isEmpty();
        RecruiterEmailTemplates.Mail m = RecruiterEmailTemplates.confirmation("<script>alert(1)</script>", "Welder <i>", "https://app/x?a=1&b=\"2\"");
        assertThat(m.body()).doesNotContain("<script>").contains("&lt;script&gt;").contains("Welder &lt;i&gt;").contains("a=1&amp;b=&quot;2&quot;");
    }

    @Test
    void aSubjectStaysOnOneLine() {
        assertThat(RecruiterEmailTemplates.stageChange("T", "Welder\r\nBcc: x@y.z", "Under Review", "https://p").subject())
                .isEqualTo("Application update — Welder Bcc: x@y.z");
    }

    @Test
    void theRejectionEmailNeverCarriesTheInternalReason() {
        RecruiterEmailTemplates.Mail m = RecruiterEmailTemplates.rejected("Thandi", "Welder");
        assertThat(m.subject()).isEqualTo("Application outcome — Welder");
        assertThat(m.body()).contains("Thandi").contains("will not be moving forward");
    }

    @Test
    void interviewEmailsShowTheDetailsThatExist() {
        RecruiterEmailTemplates.Mail m = RecruiterEmailTemplates.interviewScheduled("Thandi", "Welder", "VIDEO", "02 Nov 2026, 10:00", "Sipho",
                List.of("Lerato", "<Naledi>"), "https://meet/x", true, "https://p");
        assertThat(m.body()).contains("Interviewer: <strong>Sipho").contains("Also interviewing: <strong>Lerato, &lt;Naledi&gt;").contains("Meeting link").contains("02 Nov 2026, 10:00");
        RecruiterEmailTemplates.Mail bare = RecruiterEmailTemplates.interviewScheduled("T", "J", "PHONE", "to be confirmed", null, List.of(), null, false, "https://p");
        assertThat(bare.body()).doesNotContain("Interviewer").doesNotContain("Venue").doesNotContain("Also interviewing");
        RecruiterEmailTemplates.Mail re = RecruiterEmailTemplates.interviewRescheduled("T", "J", "Panel unavailable", "03 Nov 2026, 09:00", null, "Room 2", false, "https://p");
        assertThat(re.body()).contains("Reason: Panel unavailable").contains("Venue: <strong>Room 2");
        assertThat(RecruiterEmailTemplates.interviewRescheduled("T", "J", " ", "x", null, null, false, "https://p").body()).doesNotContain("Reason");
    }

    @Test
    void theOfferEmailStatesTheDate() {
        assertThat(RecruiterEmailTemplates.offer("Thandi", "Welder", "2026-11-11").body()).contains("open until 2026-11-11").contains("attached");
    }
}
