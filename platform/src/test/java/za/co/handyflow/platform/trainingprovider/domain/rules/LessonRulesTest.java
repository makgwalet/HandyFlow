package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LessonRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);

    @Test
    void lessonShapeDependsOnKind() {
        assertNull(LessonRules.lessonProblem("Intro", "READING", "A reading that is long enough to count", null, 1));
        assertNotNull(LessonRules.lessonProblem("Intro", "READING", "short", null, 1));
        assertNotNull(LessonRules.lessonProblem("Intro", "READING", "A reading that is long enough to count", "https://x.org", 1));
        assertNull(LessonRules.lessonProblem("Video", "LINK", null, "https://example.org/v", 2));
        assertNotNull(LessonRules.lessonProblem("Video", "LINK", null, "ftp://example.org/v", 2));
        assertNotNull(LessonRules.lessonProblem("Video", "DOCUMENT", null, null, 2));
        assertNotNull(LessonRules.lessonProblem("x", "LINK", null, "https://a.org", 1));
        assertNotNull(LessonRules.lessonProblem("Video", "AUDIO", null, "https://a.org", 1));
        assertNotNull(LessonRules.lessonProblem("Video", "LINK", null, "https://a.org", 0));
    }

    @Test
    void moduleMustBelongToTheVersion() {
        UUID v = UUID.randomUUID();
        assertNull(LessonRules.moduleProblem(v, v));
        assertNotNull(LessonRules.moduleProblem(UUID.randomUUID(), v));
    }

    @Test
    void finishChecks() {
        LocalDate start = LocalDate.of(2027, 6, 1);
        assertNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "APPROVED", false, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("CANCELLED", start, true, "APPROVED", false, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, false, "APPROVED", false, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "DRAFT", false, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "RETIRED", false, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "APPROVED", true, TODAY, TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "APPROVED", false, TODAY.plusDays(1), TODAY));
        assertNotNull(LessonRules.finishProblem("IN_PROGRESS", start, true, "APPROVED", false, start.minusDays(1), TODAY));
    }

    @Test
    void reverseNeedsReasonOnce() {
        assertNull(LessonRules.reverseProblem("FINISHED", "Marked on the wrong enrolment"));
        assertNotNull(LessonRules.reverseProblem("FINISHED", "short"));
        assertNotNull(LessonRules.reverseProblem("REVERSED", "Marked on the wrong enrolment"));
    }

    @Test
    void summaryIsProgressNotAGrade() {
        assertTrue(LessonRules.summary(2, 5).contains("2 of 5 lessons"));
        assertTrue(LessonRules.summary(2, 5).contains("not an assessment outcome"));
        assertTrue(LessonRules.summary(0, 0).contains("No approved lessons"));
    }
}
