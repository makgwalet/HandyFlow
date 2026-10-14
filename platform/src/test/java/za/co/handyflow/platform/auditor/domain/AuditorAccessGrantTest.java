package za.co.handyflow.platform.auditor.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditorAccessGrantTest {

    private AuditorAccessGrant active() {
        AuditorAccessGrant g = AuditorAccessGrant.createInvite(UUID.randomUUID(), "A@Firm.test ", UUID.randomUUID());
        g.acceptInvite(UUID.randomUUID());
        return g;
    }

    @Test
    void anInviteWithoutADateExpiresInNinetyDays() {
        AuditorAccessGrant g = AuditorAccessGrant.createInvite(UUID.randomUUID(), "a@firm.test", UUID.randomUUID());
        long days = Duration.between(Instant.now(), g.getExpiresAt()).toDays();
        assertTrue(days >= 89 && days <= 90);
    }

    @Test
    void anAcceptedGrantWorksUntilItsDateAndNotAfter() {
        AuditorAccessGrant g = active();
        assertTrue(g.isUsableAt(Instant.now()));
        assertFalse(g.isUsableAt(g.getExpiresAt()));
        assertFalse(g.isUsableAt(g.getExpiresAt().plusSeconds(1)));
    }

    @Test
    void aRevokedGrantNeverWorksAndCannotBeRedated() {
        AuditorAccessGrant g = active();
        g.revoke(UUID.randomUUID());
        assertFalse(g.isUsableAt(Instant.now()));
        assertThrows(IllegalStateException.class, () -> g.changeExpiry(Instant.now().plus(Duration.ofDays(5))));
    }

    @Test
    void redatingAnExpiredGrantBringsItBack() {
        AuditorAccessGrant g = active();
        ReflectionTestUtils.setField(g, "expiresAt", Instant.now().minusSeconds(60));
        assertFalse(g.isUsableAt(Instant.now()));
        g.changeExpiry(Instant.now().plus(Duration.ofDays(10)));
        assertTrue(g.isUsableAt(Instant.now()));
    }

    @Test
    void anInviteCannotBeAcceptedAfterItsGrantDate() {
        AuditorAccessGrant g = AuditorAccessGrant.createInvite(UUID.randomUUID(), "a@firm.test", UUID.randomUUID(), Instant.now().plusSeconds(5), "FY2026 audit");
        ReflectionTestUtils.setField(g, "expiresAt", Instant.now().minusSeconds(1));
        assertThrows(IllegalStateException.class, () -> g.acceptInvite(UUID.randomUUID()));
    }

    @Test
    void aShareNeedsAReasonAndWithdrawalNeedsOneToo() {
        UUID by = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AuditShare.share(UUID.randomUUID(), AuditShare.EVIDENCE, UUID.randomUUID(), " ", by, "A"));
        AuditShare s = AuditShare.share(UUID.randomUUID(), AuditShare.EVIDENCE, UUID.randomUUID(), "Bank statements for the audit", by, "A");
        assertTrue(s.isLive());
        assertThrows(IllegalArgumentException.class, () -> s.withdraw(by, "A", ""));
        s.withdraw(by, "A", "Wrong file");
        assertFalse(s.isLive());
        assertThrows(IllegalStateException.class, () -> s.withdraw(by, "A", "again"));
    }
}
