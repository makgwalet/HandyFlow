package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LetterTemplateRulesTest {

    @Test
    void aGoodSickNoteTemplateIsCleanedAndKeepsDays() {
        var c = LetterTemplateRules.clean(" sick note ", "  Flu rest ", null, " {{patient.name}} was seen on {{visit.date}}. ", "ignored", "URGENT", 3);
        assertEquals("SICK_NOTE", c.kind());
        assertEquals("Flu rest", c.name());
        assertEquals(3, c.unfitDays());
        assertNull(c.specialty());      // only a referral has a specialty
        assertNull(c.urgency());        // only a referral has an urgency
    }

    @Test
    void aReferralKeepsSpecialtyAndUrgencyButNotDays() {
        var c = LetterTemplateRules.clean("REFERRAL", "To cardiology", "Referral", "Please assess {{patient.name}}.", " Cardiology ", "semi-urgent", 5);
        assertEquals("Cardiology", c.specialty());
        assertEquals("SEMI_URGENT", c.urgency());
        assertNull(c.unfitDays());
    }

    @Test
    void kindNameTextAndFieldsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("MEMO", "x", null, "y", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", " ", "T", "y", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", "x", "T", " ", null, null, null));
        var e = assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", "x", "T", "Hello {{patient.age}}", null, null, null));
        assertTrue(e.getMessage().contains("{{patient.age}}") && e.getMessage().contains("{{patient.name}}"));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", "x".repeat(101), "T", "y", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", "x", "T", "y".repeat(4001), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("SICK_NOTE", "x", null, "y", null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("SICK_NOTE", "x", null, "y", null, null, 366));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("REFERRAL", "x", null, "y", null, "SOON", null));
    }

    @Test
    void aReferralMayHoldOnlyItsReasonAndALetterNeedsATitle() {
        var c = LetterTemplateRules.clean("REFERRAL", "Chest", "Chest pain work-up for {{patient.name}}", null, null, null, null);
        assertNull(c.body());
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("REFERRAL", "x", null, " ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("PRESCRIPTION_LETTER", "x", null, "Text", null, null, null));
        assertNull(LetterTemplateRules.clean("SICK_NOTE", "x", "ignored", "Text", null, null, null).title());
    }

    @Test
    void aFieldInTheTitleIsCheckedToo() {
        assertThrows(IllegalArgumentException.class, () -> LetterTemplateRules.clean("GENERAL_LETTER", "x", "For {{bogus}}", "y", null, null, null));
    }
}
