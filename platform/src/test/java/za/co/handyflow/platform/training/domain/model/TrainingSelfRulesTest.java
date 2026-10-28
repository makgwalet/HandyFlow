package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingSelfRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void aCertificateIsValidUntilAMonthBeforeItsExpiry() {
        assertEquals("VALID", TrainingSelfRules.certificateState("VALID", TODAY.plusDays(31), TODAY));
        assertEquals("EXPIRING", TrainingSelfRules.certificateState("VALID", TODAY.plusDays(30), TODAY));
        assertEquals("EXPIRING", TrainingSelfRules.certificateState("VALID", TODAY, TODAY));
    }

    @Test
    void aLapsedCertificateIsExpiredEvenBeforeTheMorningSweepMarksIt() {
        assertEquals("EXPIRED", TrainingSelfRules.certificateState("VALID", TODAY.minusDays(1), TODAY));
        assertEquals("EXPIRED", TrainingSelfRules.certificateState("EXPIRED", TODAY.minusDays(40), TODAY));
    }

    @Test
    void aWithdrawnCertificateIsWithdrawnWhateverItsDate() {
        assertEquals("REVOKED", TrainingSelfRules.certificateState("REVOKED", TODAY.plusDays(300), TODAY));
        assertEquals("REVOKED", TrainingSelfRules.certificateState("REVOKED", TODAY.minusDays(300), TODAY));
    }

    @Test
    void aCertificateWithNoExpiryNeverExpires() {
        assertEquals("VALID", TrainingSelfRules.certificateState("VALID", null, TODAY));
    }

    @Test
    void bookingsAreNamedInPlainWords() {
        assertEquals("Booked", TrainingSelfRules.bookingLabel("ENROLLED"));
        assertEquals("Did not attend", TrainingSelfRules.bookingLabel("NO_SHOW"));
        assertEquals("Cancelled", TrainingSelfRules.bookingLabel("CANCELLED"));
    }
}
