package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedFlagRulesTest {

    private static final RedFlagRules.Active A = new RedFlagRules.Active("RESP", "STRIDOR", "Stridor reported");
    private static final RedFlagRules.Active B = new RedFlagRules.Active("RESP", "CYANOSIS", "Blue lips");

    @Test
    void onlyFlagsNobodyReviewedAreOutstanding() {
        assertEquals(List.of(B), RedFlagRules.unreviewed(List.of(A, B), Set.of("RESP:STRIDOR")));
        assertTrue(RedFlagRules.unreviewed(List.of(A, B), Set.of("RESP:STRIDOR", "RESP:CYANOSIS")).isEmpty());
    }

    @Test
    void sameFlagCodeInAnotherGroupIsNotReviewed() {
        assertEquals(List.of(A), RedFlagRules.unreviewed(List.of(A), Set.of("OTHER:STRIDOR")));
    }

    @Test
    void nothingOutstandingNeedsNoReason() {
        assertNull(RedFlagRules.requireReviewedOrReason(List.of(), null));
        assertNull(RedFlagRules.requireReviewedOrReason(List.of(), "ignored"));
    }

    @Test
    void outstandingFlagsBlockSigningWithoutAReason() {
        var e = assertThrows(IllegalStateException.class, () -> RedFlagRules.requireReviewedOrReason(List.of(A, B), "  "));
        assertTrue(e.getMessage().contains("2 red flags have not been reviewed"));
        assertTrue(e.getMessage().contains("Stridor reported"));
        assertThrows(IllegalStateException.class, () -> RedFlagRules.requireReviewedOrReason(List.of(A), null));
    }

    @Test
    void aReasonLetsTheClinicianSignAndIsTrimmed() {
        assertEquals("Seen by the nurse earlier", RedFlagRules.requireReviewedOrReason(List.of(A), "  Seen by the nurse earlier "));
    }

    @Test
    void anOverLongReasonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> RedFlagRules.requireReviewedOrReason(List.of(A), "x".repeat(501)));
    }
}
