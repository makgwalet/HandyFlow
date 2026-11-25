package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmsReminderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Test
    void phoneIsTidiedButNeverGuessed() {
        assertEquals("+27821234567", SmsReminderRules.normalisePhone(" +27 82-123 4567 "));
        assertThrows(IllegalArgumentException.class, () -> SmsReminderRules.normalisePhone("0821234567"));
        assertThrows(IllegalArgumentException.class, () -> SmsReminderRules.normalisePhone(null));
        assertThrows(IllegalArgumentException.class, () -> SmsReminderRules.normalisePhone("+27abc"));
    }

    @Test
    void kindFollowsDaysAhead() {
        assertEquals("WEEK_AHEAD", SmsReminderRules.kindFor(TODAY.plusDays(7), TODAY));
        assertEquals("WEEK_AHEAD", SmsReminderRules.kindFor(TODAY.plusDays(2), TODAY));
        assertEquals("DAY_BEFORE", SmsReminderRules.kindFor(TODAY.plusDays(1), TODAY));
        assertEquals("DAY_BEFORE", SmsReminderRules.kindFor(TODAY, TODAY));
        assertNull(SmsReminderRules.kindFor(TODAY.plusDays(8), TODAY));
        assertNull(SmsReminderRules.kindFor(TODAY.minusDays(1), TODAY));
    }

    @Test
    void messageStaysWithinTheCapAndNeverClaimsDelivery() {
        String m = SmsReminderRules.message("x".repeat(500), TODAY.plusDays(1), TODAY);
        assertTrue(m.length() <= SmsReminderRules.MAX_LENGTH);
        assertTrue(m.startsWith("Due tomorrow: "));
        assertTrue(m.contains("26 Nov 2026"));
        assertTrue(SmsReminderRules.message(null, TODAY.plusDays(5), TODAY).contains("a compliance deadline"));
        assertThrows(IllegalArgumentException.class, () -> SmsReminderRules.message("A", TODAY.plusDays(20), TODAY));
    }

    @Test
    void consentNeedsABasisAndANumber() {
        TenantId t = null;
        assertThrows(IllegalArgumentException.class, () -> ComplianceSmsConsent.record(t, UUID.randomUUID(), "Thandi", "+27821234567", "short", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> ComplianceSmsConsent.record(t, UUID.randomUUID(), " ", "+27821234567", "Agreed on the phone on 3 Nov", UUID.randomUUID()));
        ComplianceSmsConsent c = ComplianceSmsConsent.record(t, UUID.randomUUID(), "Thandi", "+27 82 123 4567", "Agreed on the phone on 3 Nov", UUID.randomUUID());
        assertTrue(c.isActive());
        assertEquals("+27821234567", c.getPhone());
    }

    @Test
    void withdrawalNeedsAReasonAndHappensOnce() {
        ComplianceSmsConsent c = ComplianceSmsConsent.record(null, UUID.randomUUID(), "Thandi", "+27821234567", "Agreed on the phone on 3 Nov", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.withdraw(UUID.randomUUID(), "no"));
        c.withdraw(UUID.randomUUID(), "Asked us to stop by email");
        assertFalse(c.isActive());
        assertThrows(IllegalStateException.class, () -> c.withdraw(UUID.randomUUID(), "Asked us to stop again"));
    }

    @Test
    void outcomeWordingDoesNotClaimDelivery() {
        assertFalse(SmsReminderRules.outcomeText("HANDED_OVER").toLowerCase().contains("delivered "));
    }
}
