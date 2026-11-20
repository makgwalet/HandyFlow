package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringDomainTest {

    private final TenantId t = TenantId.of(UUID.randomUUID());
    private final UUID uid = UUID.randomUUID();

    private WatchItem item() { return WatchItem.create(t, UUID.randomUUID(), "Gazette", "g-1", "Draft rules", "https://x.example/a", "summary", LocalDate.of(2026, 11, 1), Instant.now()); }

    @Test @DisplayName("A new item is NEW; relevant needs no reason, not relevant needs one")
    void decide() {
        WatchItem a = item();
        assertEquals(WatchItemStatus.NEW, a.getStatus());
        assertThrows(IllegalArgumentException.class, () -> a.decide(WatchItemStatus.NOT_RELEVANT, "no", uid, "Sam"));
        assertThrows(IllegalArgumentException.class, () -> a.decide(WatchItemStatus.NOT_RELEVANT, null, uid, "Sam"));
        assertThrows(IllegalArgumentException.class, () -> a.decide(WatchItemStatus.NEW, "whatever", uid, "Sam"));
        assertEquals(WatchItemStatus.NEW, a.getStatus(), "failed attempts change nothing");
        a.decide(WatchItemStatus.RELEVANT, null, uid, "Sam");
        assertEquals(WatchItemStatus.RELEVANT, a.getStatus());
        assertNull(a.getDecisionNote());
        assertEquals("Sam", a.getDecidedByName());
        WatchItem b = item();
        b.decide(WatchItemStatus.NOT_RELEVANT, "Applies to mines only", uid, "Sam");
        assertEquals("Applies to mines only", b.getDecisionNote());
    }

    @Test @DisplayName("A triaged item cannot be decided again; it must be reopened with a reason, which clears the decision")
    void reopen() {
        WatchItem a = item();
        assertThrows(IllegalStateException.class, () -> a.reopen("because reasons"));
        a.decide(WatchItemStatus.NOT_RELEVANT, "Not our sector", uid, "Sam");
        assertThrows(IllegalStateException.class, () -> a.decide(WatchItemStatus.RELEVANT, null, uid, "Sam"));
        assertThrows(IllegalArgumentException.class, () -> a.reopen("x"));
        a.reopen("Sector scope changed");
        assertEquals(WatchItemStatus.NEW, a.getStatus());
        assertNull(a.getDecidedByName());
        assertNull(a.getDecidedAt());
    }

    @Test @DisplayName("An item needs a guid and a title")
    void required() {
        assertThrows(IllegalArgumentException.class, () -> WatchItem.create(t, UUID.randomUUID(), "S", " ", "T", null, null, null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> WatchItem.create(t, UUID.randomUUID(), "S", "g", "", null, null, null, Instant.now()));
    }

    @Test @DisplayName("A source starts never-fetched; failures count up, alert once on the third, and a success resets them")
    void sourceFailures() {
        WatchSource s = WatchSource.create(t, "Gazette", "Gov", "https://www.gov.za/feed", uid, "Sam");
        assertEquals(FetchStatus.NEVER, s.getLastStatus());
        assertTrue(s.isActive());
        s.recordFailure("The feed could not be reached", Instant.now());
        s.recordFailure("The feed could not be reached", Instant.now());
        assertFalse(s.justBecameAlertable());
        s.recordFailure("The feed could not be reached", Instant.now());
        assertTrue(s.justBecameAlertable());
        s.recordFailure("The feed could not be reached", Instant.now());
        assertFalse(s.justBecameAlertable(), "only the crossing failure alerts");
        assertEquals(4, s.getConsecutiveFailures());
        s.recordSuccess(3, Instant.now());
        assertEquals(0, s.getConsecutiveFailures());
        assertEquals(FetchStatus.OK, s.getLastStatus());
        assertEquals(3, s.getLastNewItems());
        assertNull(s.getLastError());
    }

    @Test @DisplayName("A source needs a name; an overlong error is clipped")
    void sourceValidation() {
        assertThrows(IllegalArgumentException.class, () -> WatchSource.create(t, " ", null, "https://www.gov.za/feed", uid, "Sam"));
        WatchSource s = WatchSource.create(t, "Gazette", " ", "https://www.gov.za/feed", uid, "Sam");
        assertNull(s.getAuthority());
        s.recordFailure("e".repeat(900), Instant.now());
        assertEquals(500, s.getLastError().length());
    }
}
