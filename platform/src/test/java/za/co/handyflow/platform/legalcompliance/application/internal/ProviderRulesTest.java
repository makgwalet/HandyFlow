package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.domain.model.AccessScope;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClientStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.ReviewCycle;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRulesTest {

    private static final Set<AccessScope> BOTH = EnumSet.allOf(AccessScope.class);
    private static final Set<AccessScope> SUMMARY = EnumSet.of(AccessScope.SUMMARY);

    @Test @DisplayName("Codes: right shape, unambiguous characters, formatting-insensitive, and only the hash is comparable")
    void codes() {
        SecureRandom rnd = new SecureRandom();
        String code = ProviderRules.newCode(rnd);
        assertEquals(14, code.length());
        assertTrue(code.matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"));
        assertTrue(ProviderRules.looksLikeCode(code));
        assertEquals(ProviderRules.hash(code), ProviderRules.hash(code.toLowerCase().replace("-", " ")));
        assertEquals(64, ProviderRules.hash(code).length());
        assertNotEquals(code, ProviderRules.hash(code));
        assertNotEquals(ProviderRules.hash(code), ProviderRules.hash(ProviderRules.newCode(rnd)));
        assertFalse(ProviderRules.looksLikeCode("ABCD-EFGH"));
        assertFalse(ProviderRules.looksLikeCode(null));
    }

    @Test @DisplayName("Invitation expiry: valid until the instant, expired from it; a missing expiry counts as expired")
    void expiry() {
        Instant now = Instant.parse("2026-11-19T10:00:00Z");
        assertFalse(ProviderRules.inviteExpired(now.plusSeconds(1), now));
        assertTrue(ProviderRules.inviteExpired(now, now));
        assertTrue(ProviderRules.inviteExpired(null, now));
    }

    @Test @DisplayName("Scopes round-trip and an empty list is empty")
    void scopes() {
        assertEquals("SUMMARY,REPORTS", ProviderRules.scopesToCsv(BOTH));
        assertEquals(BOTH, ProviderRules.parseScopes("SUMMARY,REPORTS"));
        assertTrue(ProviderRules.parseScopes("").isEmpty());
        assertTrue(ProviderRules.parseScopes(null).isEmpty());
    }

    @Test @DisplayName("Review dates follow the cycle; overdue only for active clients; reminders on the due date then weekly")
    void reviews() {
        LocalDate d = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 28), ProviderRules.nextReview(d, ReviewCycle.MONTHLY));
        assertEquals(LocalDate.of(2026, 4, 30), ProviderRules.nextReview(d, ReviewCycle.QUARTERLY));
        assertEquals(LocalDate.of(2027, 1, 31), ProviderRules.nextReview(d, ReviewCycle.ANNUALLY));
        LocalDate due = LocalDate.of(2026, 11, 10);
        assertFalse(ProviderRules.reviewOverdue(ProviderClientStatus.ACTIVE, due, due));
        assertTrue(ProviderRules.reviewOverdue(ProviderClientStatus.ACTIVE, due, due.plusDays(1)));
        assertFalse(ProviderRules.reviewOverdue(ProviderClientStatus.SUSPENDED, due, due.plusDays(30)));
        assertFalse(ProviderRules.reviewOverdue(ProviderClientStatus.ACTIVE, null, due));
        assertFalse(ProviderRules.reminderDue(due, due.minusDays(1)));
        assertTrue(ProviderRules.reminderDue(due, due));
        assertFalse(ProviderRules.reminderDue(due, due.plusDays(3)));
        assertTrue(ProviderRules.reminderDue(due, due.plusDays(14)));
        assertFalse(ProviderRules.reminderDue(null, due));
    }

    @Test @DisplayName("Only the side that suspended can resume")
    void resume() {
        assertTrue(ProviderRules.canResume("CLIENT", "CLIENT"));
        assertFalse(ProviderRules.canResume("CLIENT", "PROVIDER"));
        assertFalse(ProviderRules.canResume("PROVIDER", "CLIENT"));
        assertFalse(ProviderRules.canResume(null, "CLIENT"));
    }

    @Test @DisplayName("Access decision: needs ACTIVE, the scope granted by the client, and manager or assignment")
    void denial() {
        assertNull(ProviderRules.denial(ProviderClientStatus.ACTIVE, BOTH, AccessScope.SUMMARY, true, false));
        assertNull(ProviderRules.denial(ProviderClientStatus.ACTIVE, SUMMARY, AccessScope.SUMMARY, false, true));
        for (ProviderClientStatus s : new ProviderClientStatus[]{ProviderClientStatus.INVITED, ProviderClientStatus.SUSPENDED, ProviderClientStatus.ENDED})
            assertNotNull(ProviderRules.denial(s, BOTH, AccessScope.SUMMARY, true, true), s.name());
        assertNotNull(ProviderRules.denial(ProviderClientStatus.ACTIVE, SUMMARY, AccessScope.REPORTS, true, true));
        assertNotNull(ProviderRules.denial(ProviderClientStatus.ACTIVE, Set.of(), AccessScope.SUMMARY, true, true));
        assertEquals("You are not assigned to this client", ProviderRules.denial(ProviderClientStatus.ACTIVE, BOTH, AccessScope.REPORTS, false, false));
        // A denial gives the same answer whether the client suspended, ended or never accepted, so the consultant is not told which.
        String suspended = ProviderRules.denial(ProviderClientStatus.SUSPENDED, BOTH, AccessScope.SUMMARY, true, true);
        assertEquals(suspended, ProviderRules.denial(ProviderClientStatus.ENDED, BOTH, AccessScope.SUMMARY, true, true));
        assertEquals(suspended, ProviderRules.denial(ProviderClientStatus.INVITED, BOTH, AccessScope.SUMMARY, true, true));
        assertFalse(suspended.toLowerCase().contains("suspend") || suspended.toLowerCase().contains("ended") || suspended.toLowerCase().contains("revok"));
    }
}
