package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MoneyPostingRulesTest {

    private static final UUID AR = UUID.randomUUID(), BAD = UUID.randomUUID(), BANK = UUID.randomUUID(), CLAIM = UUID.randomUUID();
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Test
    void onlyPaymentsAndWriteOffsAreHandled() {
        assertTrue(MoneyPostingRules.handles("SCHEME_PAYMENT_RECEIVED"));
        assertTrue(MoneyPostingRules.handles("CLAIM_WRITTEN_OFF"));
        assertTrue(MoneyPostingRules.handles("SCHEME_PAYMENT_REVERSED"));
        assertFalse(MoneyPostingRules.handles("CREDIT_NOTE_ISSUED"));   // already reversed by the claim service
        assertFalse(MoneyPostingRules.handles("CLAIM_VOIDED"));
    }

    @Test
    void aSchemePaymentWaitsForABankAndAWriteOffDoesNot() {
        assertTrue(MoneyPostingRules.needsBank("SCHEME_PAYMENT_RECEIVED"));
        assertFalse(MoneyPostingRules.needsBank("CLAIM_WRITTEN_OFF"));
    }

    @Test
    void aSchemePaymentNeedsABankLedgerAccount() {
        assertNotNull(MoneyPostingRules.blocker("SCHEME_PAYMENT_RECEIVED", TEN, AR, BAD, null));
        assertNull(MoneyPostingRules.blocker("SCHEME_PAYMENT_RECEIVED", TEN, AR, null, BANK));
    }

    @Test
    void aWriteOffNeedsBadDebtsAndReceivablesButNoBank() {
        assertNull(MoneyPostingRules.blocker("CLAIM_WRITTEN_OFF", TEN, AR, BAD, null));
        assertTrue(MoneyPostingRules.blocker("CLAIM_WRITTEN_OFF", TEN, AR, null, null).contains("Bad Debts"));
        assertTrue(MoneyPostingRules.blocker("CLAIM_WRITTEN_OFF", TEN, null, BAD, null).contains("Receivable"));
    }

    @Test
    void anAmountOfZeroOrLessIsNeverPosted() {
        assertNotNull(MoneyPostingRules.blocker("CLAIM_WRITTEN_OFF", BigDecimal.ZERO, AR, BAD, null));
        assertNotNull(MoneyPostingRules.blocker("CLAIM_WRITTEN_OFF", null, AR, BAD, null));
    }

    @Test
    void paymentDebitsTheBankAndCreditsReceivables() {
        var l = MoneyPostingRules.lines("SCHEME_PAYMENT_RECEIVED", TEN, AR, BAD, BANK);
        assertEquals(BANK, l.get(0).accountId());
        assertEquals(TEN, l.get(0).debit());
        assertNull(l.get(0).credit());
        assertEquals(AR, l.get(1).accountId());
        assertEquals(TEN, l.get(1).credit());
    }

    @Test
    void writeOffDebitsBadDebtsAndCreditsReceivables() {
        var l = MoneyPostingRules.lines("CLAIM_WRITTEN_OFF", TEN, AR, BAD, null);
        assertEquals(BAD, l.get(0).accountId());
        assertEquals(TEN, l.get(0).debit());
        assertEquals(AR, l.get(1).accountId());
        assertEquals(TEN, l.get(1).credit());
    }

    @Test
    void theSameEventHasTheSameKeyWhateverTheDecimalScale() {
        Instant t = Instant.parse("2026-10-13T10:00:00.123Z");
        assertEquals(MoneyPostingRules.eventKey("CLAIM_WRITTEN_OFF", CLAIM, new BigDecimal("10.00"), t),
                MoneyPostingRules.eventKey("CLAIM_WRITTEN_OFF", CLAIM, new BigDecimal("10.0"), t));
        assertNotEquals(MoneyPostingRules.eventKey("CLAIM_WRITTEN_OFF", CLAIM, TEN, t),
                MoneyPostingRules.eventKey("SCHEME_PAYMENT_RECEIVED", CLAIM, TEN, t));
    }

    @Test
    void descriptionNamesTheClaimAndReference() {
        assertTrue(MoneyPostingRules.description("SCHEME_PAYMENT_RECEIVED", CLAIM, " REM-77 ").contains("(REM-77)"));
        assertFalse(MoneyPostingRules.description("CLAIM_WRITTEN_OFF", CLAIM, null).contains("("));
    }

    @Test
    void aReversedPaymentPostsTheOppositeOfThePaymentAndNeedsABank() {
        assertTrue(MoneyPostingRules.needsBank("SCHEME_PAYMENT_REVERSED"));
        assertNotNull(MoneyPostingRules.blocker("SCHEME_PAYMENT_REVERSED", TEN, AR, BAD, null));
        assertNull(MoneyPostingRules.blocker("SCHEME_PAYMENT_REVERSED", TEN, AR, BAD, BANK));
        var lines = MoneyPostingRules.lines("SCHEME_PAYMENT_REVERSED", TEN, AR, BAD, BANK);
        assertEquals(AR, lines.get(0).accountId());
        assertEquals(TEN, lines.get(0).debit());
        assertEquals(BANK, lines.get(1).accountId());
        assertEquals(TEN, lines.get(1).credit());
        assertTrue(MoneyPostingRules.description("SCHEME_PAYMENT_REVERSED", CLAIM, null).startsWith("Scheme payment reversed"));
    }
}
