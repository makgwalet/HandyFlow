package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.InspectionRules;
import za.co.handyflow.platform.earthmoving.domain.model.InspectionRules.Answer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InspectionRulesTest {

    static List<Answer> allOk() {
        List<Answer> l = new ArrayList<>();
        for (String item : InspectionRules.CHECKLIST) l.add(new Answer(item, "OK", null));
        return l;
    }
    static List<Answer> with(int index, String outcome, String comment) {
        List<Answer> l = allOk();
        l.set(index, new Answer(InspectionRules.CHECKLIST.get(index), outcome, comment));
        return l;
    }

    @Test void aCleanWalkRoundPasses() {
        List<Answer> a = InspectionRules.check(allOk());
        assertEquals(InspectionRules.CHECKLIST.size(), a.size());
        assertEquals("PASS", InspectionRules.overall(a));
    }
    @Test void everyItemMustBeAnswered() {
        List<Answer> l = allOk();
        l.remove(3);
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(l));
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(List.of()));
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(null));
    }
    @Test void aDefectNeedsAComment() {
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(with(1, "DEFECT", null)));
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(with(1, "UNSAFE", "bad")));
        assertEquals("DEFECTS", InspectionRules.overall(InspectionRules.check(with(1, "DEFECT", "Pedal feels soft"))));
    }
    @Test void oneUnsafeFindingMakesTheWholeInspectionUnsafe() {
        List<Answer> l = with(0, "DEFECT", "Worn tread on left rear");
        l.set(2, new Answer(InspectionRules.CHECKLIST.get(2), "UNSAFE", "Steering play, wheel will not hold"));
        assertEquals("UNSAFE", InspectionRules.overall(InspectionRules.check(l)));
    }
    @Test void anUnknownOutcomeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.check(with(0, "FINE", null)));
        assertEquals("OK", InspectionRules.check(with(0, " ok ", null)).get(0).outcome());
    }
    @Test void itemsAreMatchedByNameWhateverTheOrderOrCase() {
        List<Answer> l = allOk();
        java.util.Collections.reverse(l);
        l.set(0, new Answer(l.get(0).item().toUpperCase(), "OK", null));
        List<Answer> a = InspectionRules.check(l);
        assertEquals(InspectionRules.CHECKLIST.get(0), a.get(0).item());
    }
    @Test void aCommentOnAnOkItemIsKept() {
        assertEquals("Slight stain, wiped", InspectionRules.check(with(4, "OK", " Slight stain, wiped ")).get(4).comment());
    }

    @Test void typeDefaultsToPreUseAndRejectsUnknownTypes() {
        assertEquals("PRE_USE", InspectionRules.type(null));
        assertEquals("PRE_USE", InspectionRules.type("  "));
        assertEquals("HANDOVER", InspectionRules.type(" handover "));
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.type("WEEKLY"));
    }
    @Test void onlyHandoverAndReturnNeedAMeterReading() {
        assertDoesNotThrow(() -> InspectionRules.checkMeter("PRE_USE", null));
        assertDoesNotThrow(() -> InspectionRules.checkMeter("RETURN", java.math.BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> InspectionRules.checkMeter("HANDOVER", null));
    }
}
