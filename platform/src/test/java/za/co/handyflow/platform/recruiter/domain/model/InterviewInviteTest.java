package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewInviteTest {
    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Test
    void anInterviewWithoutATimeHasNoInvite() {
        assertThat(InterviewInvite.problem(null)).contains("no time");
        assertThat(InterviewInvite.problem(START)).isNull();
    }

    @Test
    void theFileHasTheTimesAndEndsWithCrLf() {
        String ics = InterviewInvite.build("abc", START, 45, "Interview: Thandi", "VIDEO interview", "Room 2, Sandton", "Sipho", NOW);
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("DTSTART:20261102T080000Z\r\n").contains("DTEND:20261102T084500Z\r\n").contains("DTSTAMP:20261028T100000Z\r\n");
        assertThat(ics).contains("UID:abc@handyflow\r\n").contains("LOCATION:Room 2\\, Sandton\r\n");
    }

    @Test
    void specialCharactersAreEscaped() {
        assertThat(InterviewInvite.escape("a;b,c\\d\ne")).isEqualTo("a\\;b\\,c\\\\d\\ne");
    }

    @Test
    void longLinesAreFolded() {
        String ics = InterviewInvite.build("abc", START, 60, "x".repeat(200), null, null, null, NOW);
        for (String l : ics.split("\r\n")) assertThat(l.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + "x".repeat(200));
    }
}
