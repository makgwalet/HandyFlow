package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationReturn;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.ReturnStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeclarationRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 12, 15);

    @Test @DisplayName("Reminders: 7 days before, the day before, on the day, then weekly while overdue; nothing on other days")
    void reminderDays() {
        assertTrue(DeclarationRules.remindToday(DUE, DUE.minusDays(7)));
        assertTrue(DeclarationRules.remindToday(DUE, DUE.minusDays(1)));
        assertTrue(DeclarationRules.remindToday(DUE, DUE));
        assertTrue(DeclarationRules.remindToday(DUE, DUE.plusDays(7)));
        assertTrue(DeclarationRules.remindToday(DUE, DUE.plusDays(14)));
        for (int d : new int[]{30, 14, 10, 8, 6, 5, 4, 3, 2}) assertFalse(DeclarationRules.remindToday(DUE, DUE.minusDays(d)), "due in " + d + " days");
        for (int d : new int[]{1, 2, 6, 8, 13}) assertFalse(DeclarationRules.remindToday(DUE, DUE.plusDays(d)), d + " days overdue");
    }

    @Test @DisplayName("Reminder wording says how close or late it is")
    void reminderText() {
        assertEquals("due in 7 days (2026-12-15)", DeclarationRules.reminderText(DUE, DUE.minusDays(7)));
        assertEquals("due tomorrow (2026-12-15)", DeclarationRules.reminderText(DUE, DUE.minusDays(1)));
        assertEquals("due today", DeclarationRules.reminderText(DUE, DUE));
        assertEquals("overdue by 1 day (was due 2026-12-15)", DeclarationRules.reminderText(DUE, DUE.plusDays(1)));
        assertEquals("overdue by 7 days (was due 2026-12-15)", DeclarationRules.reminderText(DUE, DUE.plusDays(7)));
    }

    @Test @DisplayName("Only live declarations can be confirmed as unchanged")
    void confirmable() {
        assertTrue(DeclarationRules.confirmable(DeclarationStatus.DECLARED));
        assertTrue(DeclarationRules.confirmable(DeclarationStatus.UNDER_REVIEW));
        assertTrue(DeclarationRules.confirmable(DeclarationStatus.CLEARED));
        assertTrue(DeclarationRules.confirmable(DeclarationStatus.MITIGATED));
        assertFalse(DeclarationRules.confirmable(DeclarationStatus.NOT_PERMITTED));
        assertFalse(DeclarationRules.confirmable(DeclarationStatus.WITHDRAWN));
    }

    @Test @DisplayName("Percentages are whole numbers, and zero for an empty campaign")
    void percent() {
        assertEquals(0, DeclarationRules.percent(0, 0));
        assertEquals(67, DeclarationRules.percent(2, 3));
        assertEquals(100, DeclarationRules.percent(5, 5));
    }

    private static DeclarationReturn fresh() { return DeclarationReturn.assign(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), "Anna", "a@x.co.za"); }

    @Test @DisplayName("A return starts pending; answering records the exact words confirmed")
    void answers() {
        DeclarationReturn r = fresh();
        assertTrue(r.isPending());
        r.answerNil();
        assertEquals(ReturnStatus.NIL, r.getStatus());
        assertEquals(DeclarationReturn.NIL_STATEMENT, r.getStatement());
        assertNotNull(r.getRespondedAt());
        r.answerUnchanged(2);
        assertEquals(ReturnStatus.UNCHANGED, r.getStatus());
        assertEquals(DeclarationReturn.UNCHANGED_STATEMENT, r.getStatement());
        r.answerNil();
        assertEquals(ReturnStatus.NIL, r.getStatus(), "an answer can be changed while the campaign is open");
    }

    @Test @DisplayName("'Unchanged' needs an earlier declaration to vouch for")
    void unchangedNeedsExisting() {
        DeclarationReturn r = fresh();
        assertThrows(IllegalArgumentException.class, () -> r.answerUnchanged(0));
        assertTrue(r.isPending());
    }

    @Test @DisplayName("Once an interest is declared the return is DECLARED, keeps every declaration once, and cannot go back to 'nothing to declare'")
    void declared() {
        DeclarationReturn r = fresh();
        r.answerNil();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        r.addDeclaration(a);
        r.addDeclaration(b);
        r.addDeclaration(a);
        assertEquals(ReturnStatus.DECLARED, r.getStatus());
        assertEquals(java.util.List.of(a, b), r.declaredIds());
        assertNull(r.getStatement());
        assertThrows(IllegalStateException.class, r::answerNil);
        assertThrows(IllegalStateException.class, () -> r.answerUnchanged(3));
        assertEquals(ReturnStatus.DECLARED, r.getStatus());
    }

    @Test @DisplayName("Only cleared or mitigated declarations have a review date for 'unchanged' to extend")
    void extendsReview() {
        assertTrue(DeclarationRules.extendsReview(DeclarationStatus.CLEARED));
        assertTrue(DeclarationRules.extendsReview(DeclarationStatus.MITIGATED));
        for (DeclarationStatus s : new DeclarationStatus[]{DeclarationStatus.DECLARED, DeclarationStatus.UNDER_REVIEW, DeclarationStatus.NOT_PERMITTED, DeclarationStatus.WITHDRAWN})
            assertFalse(DeclarationRules.extendsReview(s), s.name());
    }

    @Test @DisplayName("A review date already set to a full period from today counts as already confirmed")
    void alreadyConfirmed() {
        LocalDate today = LocalDate.of(2026, 11, 19);
        assertTrue(DeclarationRules.alreadyConfirmed(today.plusMonths(12), 12, today));
        assertTrue(DeclarationRules.alreadyConfirmed(today.plusMonths(6), 6, today));
        assertFalse(DeclarationRules.alreadyConfirmed(today.plusMonths(11), 12, today));
        assertFalse(DeclarationRules.alreadyConfirmed(today.minusDays(5), 12, today));
        assertFalse(DeclarationRules.alreadyConfirmed(null, 12, today));
    }

    @Test @DisplayName("An unanswered return can be excused and put back; an answered one is kept as it is")
    void leftAndBack() {
        DeclarationReturn r = fresh();
        r.markLeft();
        assertEquals(ReturnStatus.LEFT, r.getStatus());
        assertFalse(r.isPending());
        r.markLeft();
        assertEquals(ReturnStatus.LEFT, r.getStatus(), "excusing twice changes nothing");
        r.reinstate();
        assertTrue(r.isPending());
        assertThrows(IllegalStateException.class, r::reinstate);
        DeclarationReturn answered = fresh();
        answered.answerNil();
        assertThrows(IllegalStateException.class, answered::markLeft);
        assertEquals(ReturnStatus.NIL, answered.getStatus());
    }

    @Test @DisplayName("Someone who has been excused cannot answer or declare until put back")
    void leftCannotAnswer() {
        DeclarationReturn r = fresh();
        r.markLeft();
        assertThrows(IllegalStateException.class, r::answerNil);
        assertThrows(IllegalStateException.class, () -> r.answerUnchanged(2));
        assertThrows(IllegalStateException.class, () -> r.addDeclaration(UUID.randomUUID()));
        assertEquals(ReturnStatus.LEFT, r.getStatus());
        r.reinstate();
        r.answerNil();
        assertEquals(ReturnStatus.NIL, r.getStatus());
    }
}
