package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationPolicy.Decision;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationPolicy.Input;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationPolicyTest {

    private CommunicationPolicyProperties props;
    private CommunicationPolicy policy;
    private static final LocalTime NOON = LocalTime.of(12, 0);

    @BeforeEach
    void setUp() {
        props = new CommunicationPolicyProperties();
        policy = new CommunicationPolicy(props);
    }

    private Decision eval(CommunicationChannel ch, CommunicationPurpose p, CaseStatus s, boolean optedOut, LocalTime t, long today) {
        return policy.evaluate(new Input(ch, p, s, optedOut, t, today));
    }

    @Test
    void whatsappIsBlockedByDefaultAndExplainsWhy() {
        Decision d = eval(CommunicationChannel.WHATSAPP, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, NOON, 0);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("prohibited"));
        assertFalse(policy.enabledChannels().contains(CommunicationChannel.WHATSAPP));
    }

    @Test
    void whatsappAllowedOnlyWhenEligibilityConfirmed() {
        props.setWhatsappEligibilityConfirmed(true);
        assertTrue(eval(CommunicationChannel.WHATSAPP, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, NOON, 0).allowed());
    }

    @Test
    void ordinaryReminderAllowed() {
        assertTrue(eval(CommunicationChannel.EMAIL, CommunicationPurpose.REMINDER, CaseStatus.DEMAND_SENT, false, NOON, 0).allowed());
    }

    @Test
    void collectionPausedWhileDisputedButDisputeResponseStillAllowed() {
        assertFalse(eval(CommunicationChannel.EMAIL, CommunicationPurpose.DEMAND, CaseStatus.DISPUTED, false, NOON, 0).allowed());
        assertTrue(eval(CommunicationChannel.EMAIL, CommunicationPurpose.DISPUTE_RESPONSE, CaseStatus.DISPUTED, false, NOON, 0).allowed());
    }

    @Test
    void collectionBlockedOnceHandedToLegalOrClosed() {
        assertFalse(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.HANDED_TO_LEGAL, false, NOON, 0).allowed());
        assertFalse(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.CLOSED, false, NOON, 0).allowed());
    }

    @Test
    void optOutBlocksEvenNonCollectionPurposes() {
        assertFalse(eval(CommunicationChannel.SMS, CommunicationPurpose.PAYMENT_ACKNOWLEDGEMENT, CaseStatus.OPEN, true, NOON, 0).allowed());
    }

    @Test
    void quietHoursWrapMidnightAndApplyToSmsOnly() {
        props.setQuietHoursStart(20);
        props.setQuietHoursEnd(8);
        assertTrue(policy.inQuietHours(LocalTime.of(21, 0)));
        assertTrue(policy.inQuietHours(LocalTime.of(7, 59)));
        assertFalse(policy.inQuietHours(LocalTime.of(8, 0)));
        assertFalse(policy.inQuietHours(NOON));
        assertFalse(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, LocalTime.of(22, 0), 0).allowed());
        assertTrue(eval(CommunicationChannel.EMAIL, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, LocalTime.of(22, 0), 0).allowed());
    }

    @Test
    void dailyCapAppliesWhenConfigured() {
        assertTrue(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, NOON, 50).allowed()); // no cap by default
        props.setDailyCapPerCase(2);
        assertTrue(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, NOON, 1).allowed());
        assertFalse(eval(CommunicationChannel.SMS, CommunicationPurpose.REMINDER, CaseStatus.OPEN, false, NOON, 2).allowed());
    }
}
