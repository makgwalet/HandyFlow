package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamGroupRulesTest {

    private static final List<Map<String, Object>> OPTS = List.of(Map.of("value", "clear", "label", "Clear"), Map.of("value", "wheeze", "label", "Wheeze"));

    @Test
    void examinationIsRecognisedByCategoryIgnoringCase() {
        assertTrue(ExamGroupRules.isExamination("EXAMINATION"));
        assertTrue(ExamGroupRules.isExamination(" examination "));
        assertFalse(ExamGroupRules.isExamination("HISTORY"));
        assertFalse(ExamGroupRules.isExamination(null));
    }

    @Test
    void eachPageGetsOnlyItsOwnGroups() {
        assertTrue(ExamGroupRules.onPage("EXAMINATION", true));
        assertFalse(ExamGroupRules.onPage("EXAMINATION", false));
        assertTrue(ExamGroupRules.onPage(null, false));
        assertFalse(ExamGroupRules.onPage(null, true));
    }

    @Test
    void noPresetIsAlwaysFine() {
        assertNull(ExamGroupRules.normalProblem("NUMBER", null, null));
    }

    @Test
    void presetMustFitTheAnswerType() {
        assertNull(ExamGroupRules.normalProblem("YES_NO", null, Boolean.FALSE));
        assertNotNull(ExamGroupRules.normalProblem("YES_NO", null, "no"));
        assertNull(ExamGroupRules.normalProblem("SINGLE_SELECT", OPTS, "clear"));
        assertNotNull(ExamGroupRules.normalProblem("SINGLE_SELECT", OPTS, "crackles"));
        assertNull(ExamGroupRules.normalProblem("CHECKLIST", OPTS, List.of("clear")));
        assertNotNull(ExamGroupRules.normalProblem("CHECKLIST", OPTS, List.of("clear", "other")));
        assertNotNull(ExamGroupRules.normalProblem("CHECKLIST", OPTS, List.of()));
        assertNull(ExamGroupRules.normalProblem("TEXT", null, "No abnormality detected"));
        assertNotNull(ExamGroupRules.normalProblem("TEXT", null, "  "));
    }

    @Test
    void measurementsAndNumbersAreNeverPreset() {
        assertTrue(ExamGroupRules.normalProblem("NUMBER", null, 5).contains("never preset"));
        assertNotNull(ExamGroupRules.normalProblem("MEASUREMENT", null, 36.6));
        assertEquals(null, ExamGroupRules.normalProblem("DECIMAL", null, null));
    }
}
