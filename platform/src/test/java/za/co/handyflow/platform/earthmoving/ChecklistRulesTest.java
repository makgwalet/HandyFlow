package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.ChecklistRules;
import za.co.handyflow.platform.earthmoving.domain.model.ChecklistRules.*;
import za.co.handyflow.platform.earthmoving.domain.model.InspectionRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChecklistRulesTest {

    static Question q(String text, String type, boolean required) {
        return new Question(null, null, text, type, required, false, null, null, null, "DEFECT");
    }
    static Question number(String text, String min, String max, String fail) {
        return new Question(null, null, text, "NUMERIC", true, false, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), "bar", fail);
    }
    static List<Question> withExtras(Question... extra) {
        List<Question> l = new ArrayList<>(ChecklistRules.builtIn());
        l.addAll(List.of(extra));
        return l;
    }
    static List<Given> allOk(List<Question> qs) {
        List<Given> l = new ArrayList<>();
        for (Question x : qs) if (x.answerType().equals("PASS_FAIL")) l.add(new Given(x.text(), "OK", null, null, null, null));
        return l;
    }

    @Test void theBuiltInWalkRoundIsTheTenItemMinimum() {
        assertEquals(10, ChecklistRules.builtIn().size());
        assertTrue(ChecklistRules.builtIn().stream().allMatch(x -> x.builtIn() && x.required() && x.answerType().equals("PASS_FAIL")));
        assertTrue(ChecklistRules.builtInActive("PRE_USE").builtInDefault());
    }

    @Test void aCompanyChecklistMustKeepTheBuiltInItemsForTheWalkRoundTypes() {
        List<Question> missing = new ArrayList<>(ChecklistRules.builtIn());
        missing.remove(1);
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("PRE_USE", missing));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("RETURN", missing));
        assertDoesNotThrow(() -> ChecklistRules.clean("MAINTENANCE", List.of(q("Grease points done", "PASS_FAIL", true))));
        assertDoesNotThrow(() -> ChecklistRules.clean("SAFETY", missing));
    }

    @Test void aCompanyCanAddButNotWeakenABuiltInItem() {
        List<Question> weak = new ArrayList<>(ChecklistRules.builtIn());
        weak.set(0, new Question(null, null, "tyres or tracks", "PASS_FAIL", false, false, null, null, null, "DEFECT"));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("PRE_USE", weak));
        List<Question> typed = new ArrayList<>(ChecklistRules.builtIn());
        typed.set(0, new Question(null, null, "Tyres or tracks", "TEXT", true, false, null, null, null, "DEFECT"));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("PRE_USE", typed));
        List<Question> cleaned = ChecklistRules.clean("PRE_USE", withExtras(q("Greased", "PASS_FAIL", true)));
        assertEquals(11, cleaned.size());
        assertTrue(cleaned.get(0).builtIn());
        assertFalse(cleaned.get(10).builtIn());
    }

    @Test void aBuiltInItemIsMarkedEvenWhenTheCaseDiffers() {
        List<Question> l = new ArrayList<>(ChecklistRules.builtIn());
        l.set(1, new Question(null, null, " BRAKES ", "PASS_FAIL", true, false, null, null, null, "DEFECT"));
        List<Question> out = ChecklistRules.clean("PRE_USE", l);
        assertEquals("Brakes", out.get(1).text());
        assertTrue(out.get(1).builtIn());
    }

    @Test void questionsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of()));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(q(" ", "PASS_FAIL", true))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(q("x".repeat(81), "PASS_FAIL", true))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(q("Horn", "PASS_FAIL", true), q("horn", "PASS_FAIL", true))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(q("Horn", "VIDEO", true))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(number("Pressure", null, null, "DEFECT"))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(number("Pressure", "9", "3", "DEFECT"))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", List.of(number("Pressure", "3", "9", "FATAL"))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.clean("SAFETY", java.util.Collections.nCopies(61, q("x", "TEXT", true))));
    }

    @Test void answerTypeDefaultsToPassFail() {
        assertEquals("PASS_FAIL", ChecklistRules.answerType(null));
        assertEquals("NUMERIC", ChecklistRules.answerType(" numeric "));
    }

    @Test void aVersionCannotStartInThePast() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        assertEquals(today, ChecklistRules.effectiveFrom(null, today));
        assertEquals(today.plusDays(7), ChecklistRules.effectiveFrom(today.plusDays(7), today));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.effectiveFrom(today.minusDays(1), today));
    }

    @Test void signOffIsOnlyAskedForWhenTheChecklistRequiresIt() {
        assertDoesNotThrow(() -> ChecklistRules.checkSignoff(false, null));
        assertDoesNotThrow(() -> ChecklistRules.checkSignoff(true, "Thabo M"));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.checkSignoff(true, " "));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.checkSignoff(true, "TM"));
    }

    @Test void aCleanBuiltInInspectionPasses() {
        List<Checked> out = ChecklistRules.check(ChecklistRules.builtIn(), allOk(ChecklistRules.builtIn()));
        assertEquals(10, out.size());
        assertEquals("PASS", ChecklistRules.overall(out));
    }

    @Test void aMissingRequiredQuestionIsRefusedAndAnUnknownOneToo() {
        List<Given> given = allOk(ChecklistRules.builtIn());
        given.remove(3);
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(ChecklistRules.builtIn(), given));
        List<Given> extra = allOk(ChecklistRules.builtIn());
        extra.add(new Given("Something old", "OK", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(ChecklistRules.builtIn(), extra));
    }

    @Test void aNonOkAnswerNeedsWhatWasSeen() {
        List<Given> given = allOk(ChecklistRules.builtIn());
        given.set(1, new Given("Brakes", "UNSAFE", "bad", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(ChecklistRules.builtIn(), given));
        given.set(1, new Given("Brakes", "UNSAFE", "Pedal goes to the floor", null, null, null));
        assertEquals("UNSAFE", ChecklistRules.overall(ChecklistRules.check(ChecklistRules.builtIn(), given)));
    }

    @Test void aNumberInsideItsRangeIsOkAndOutsideItIsTheQuestionsFailOutcome() {
        List<Question> qs = List.of(number("Hydraulic pressure", "180", "220", "UNSAFE"), number("Oil level", "2", null, "DEFECT"));
        List<Checked> ok = ChecklistRules.check(qs, List.of(
                new Given("Hydraulic pressure", null, null, new BigDecimal("200"), null, null), new Given("Oil level", null, null, new BigDecimal("2"), null, null)));
        assertEquals("PASS", ChecklistRules.overall(ok));
        assertEquals(new BigDecimal("200"), ok.get(0).number());

        List<Given> high = List.of(new Given("Hydraulic pressure", null, "Gauge reads high after warm-up", new BigDecimal("240"), null, null),
                new Given("Oil level", null, null, new BigDecimal("3"), null, null));
        Checked c = ChecklistRules.check(qs, high).get(0);
        assertEquals("UNSAFE", c.outcome());
        assertEquals("UNSAFE", ChecklistRules.overall(ChecklistRules.check(qs, high)));

        List<Given> low = List.of(new Given("Hydraulic pressure", null, null, new BigDecimal("200"), null, null),
                new Given("Oil level", null, "Dipstick shows low", new BigDecimal("1.5"), null, null));
        assertEquals("DEFECTS", ChecklistRules.overall(ChecklistRules.check(qs, low)));
    }

    @Test void aNumberOutsideItsRangeNeedsAComment() {
        List<Question> qs = List.of(number("Hydraulic pressure", "180", "220", "DEFECT"));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(qs, List.of(new Given("Hydraulic pressure", null, null, new BigDecimal("100"), null, null))));
    }

    @Test void aMissingNumberIsOnlyAllowedWhenNotRequired() {
        Question optional = new Question(null, null, "Hours since grease", "NUMERIC", false, false, null, new BigDecimal("50"), "h", "DEFECT");
        List<Question> qs = List.of(q("Horn", "PASS_FAIL", true), optional);
        List<Checked> out = ChecklistRules.check(qs, List.of(new Given("Horn", "OK", null, null, null, null)));
        assertEquals(1, out.size());
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(List.of(q("Horn", "PASS_FAIL", true), number("P", "1", "2", "DEFECT")),
                List.of(new Given("Horn", "OK", null, null, null, null))));
    }

    @Test void textAndPhotoQuestionsStoreTheirValueAndNeverRaiseFindings() {
        List<Question> qs = List.of(q("Operator remarks", "TEXT", true), q("Photo of plate", "PHOTO", true));
        List<Checked> out = ChecklistRules.check(qs, List.of(
                new Given("Operator remarks", null, null, null, " Slight noise on start ", null), new Given("Photo of plate", null, null, null, null, "camera-0001.jpg")));
        assertEquals("Slight noise on start", out.get(0).text());
        assertEquals("camera-0001.jpg", out.get(1).evidence());
        assertEquals("PASS", ChecklistRules.overall(out));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(qs, List.of(new Given("Operator remarks", null, null, null, "ok", null))));
        assertThrows(IllegalArgumentException.class, () -> ChecklistRules.check(qs, List.of(new Given("Photo of plate", null, null, null, null, "x"))));
    }

    @Test void maintenanceAndSafetyAreInspectionTypesButNeedNoMeter() {
        assertEquals("MAINTENANCE", InspectionRules.type("maintenance"));
        assertEquals("SAFETY", InspectionRules.type("Safety"));
        assertDoesNotThrow(() -> InspectionRules.checkMeter("MAINTENANCE", null));
        assertDoesNotThrow(() -> InspectionRules.checkMeter("SAFETY", null));
    }
}
