package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** What the entities remember about reminders: a person's "dealt with" and the sweep's "told on" are separate. */
class AgReminderStateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static AgInventoryItem item(String qty, String reorder) {
        AgInventoryItem i = AgInventoryItem.create(TenantId.generate(), UUID.randomUUID(), "Maize meal", "FEED", "kg", new BigDecimal(reorder), null, null);
        i.receive(new BigDecimal(qty), null);
        return i;
    }

    @Test void beingToldAboutLowStockDoesNotChangeTheStock() {
        AgInventoryItem i = item("3", "10");
        i.markLowStockNotified(DAY);
        assertEquals(DAY, i.getLowStockNotifiedOn());
        assertEquals(0, new BigDecimal("3").compareTo(i.getCurrentQuantity()));
        assertTrue(i.isBelowReorderLevel());
    }

    @Test void restockingAboveTheReorderLevelForgetsTheNotice() {
        AgInventoryItem i = item("3", "10");
        i.markLowStockNotified(DAY);
        i.receive(new BigDecimal("2"), null);                      // 5, still low: the notice stands
        assertEquals(DAY, i.getLowStockNotifiedOn());
        i.receive(new BigDecimal("20"), null);                     // 25, restocked
        assertNull(i.getLowStockNotifiedOn());
    }

    @Test void aStockCountAboveTheLevelForgetsTheNoticeToo() {
        AgInventoryItem i = item("3", "10");
        i.markLowStockNotified(DAY);
        i.adjust(new BigDecimal("2"));
        assertEquals(DAY, i.getLowStockNotifiedOn());
        i.adjust(new BigDecimal("50"));
        assertNull(i.getLowStockNotifiedOn());
    }

    @Test void issuingStockDoesNotForgetTheNotice() {
        AgInventoryItem i = item("12", "10");
        i.issue(new BigDecimal("5"));
        i.markLowStockNotified(DAY);
        i.issue(new BigDecimal("1"));
        assertEquals(DAY, i.getLowStockNotifiedOn());
    }

    @Test void changingTheReorderLevelStartsANewNotice() {
        AgInventoryItem i = item("3", "10");
        i.markLowStockNotified(DAY);
        i.update(null, new BigDecimal("10"), null, null, "same level");
        assertEquals(DAY, i.getLowStockNotifiedOn());
        i.update(null, new BigDecimal("20"), null, null, "raised");
        assertNull(i.getLowStockNotifiedOn());
    }

    private static AgHealthEvent health(LocalDate due) {
        return AgHealthEvent.create(TenantId.generate(), UUID.randomUUID(), null, "VACCINATION", DAY.minusDays(30), "Anthrax",
                null, null, null, null, null, null, null, due, null, null);
    }

    @Test void aSweepNoticeAndAPersonsAcknowledgementAreSeparate() {
        AgHealthEvent e = health(DAY);
        e.markReminderNotified(DAY);
        assertEquals(DAY, e.getReminderNotifiedOn());
        assertFalse(e.isReminderAcknowledged(), "being told must not hide it from the dashboard");
        e.acknowledgeReminder();
        assertTrue(e.isReminderAcknowledged());
        assertEquals(DAY, e.getReminderNotifiedOn());
    }

    @Test void editingOtherFieldsOfAHealthEventKeepsItsReminderState() {
        AgHealthEvent e = health(DAY);
        e.markReminderNotified(DAY);
        e.acknowledgeReminder();
        e.update("Anthrax booster", null, null, null, null, null, DAY, "a note");
        assertTrue(e.isReminderAcknowledged());
        assertEquals(DAY, e.getReminderNotifiedOn());
    }

    @Test void aNewDueDateIsANewHealthReminder() {
        AgHealthEvent e = health(DAY);
        e.markReminderNotified(DAY);
        e.acknowledgeReminder();
        e.update(null, null, null, null, null, null, DAY.plusDays(180), null);
        assertFalse(e.isReminderAcknowledged());
        assertNull(e.getReminderNotifiedOn());
    }

    private static AgScoutingRecord scouting(LocalDate followUp) {
        return AgScoutingRecord.create(TenantId.generate(), UUID.randomUUID(), DAY.minusDays(3), "PEST", "HIGH", "Aphids",
                null, null, null, followUp, null);
    }

    @Test void scoutingFollowUpKeepsStateUnlessTheDateChanges() {
        AgScoutingRecord r = scouting(DAY);
        r.markFollowUpNotified(DAY);
        r.acknowledgeFollowUp();
        r.update(null, "Aphids, north block", null, DAY, "more detail");
        assertTrue(r.isFollowUpAcknowledged());
        assertEquals(DAY, r.getFollowUpNotifiedOn());
        r.update(null, null, null, DAY.plusDays(7), null);
        assertFalse(r.isFollowUpAcknowledged());
        assertNull(r.getFollowUpNotifiedOn());
    }
}
