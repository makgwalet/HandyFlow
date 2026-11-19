package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommitteeDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 6, 1);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), committeeId = UUID.randomUUID();
    private static final String TXT = "A sufficiently long text for the rule";
    private static final String MIN = "Minutes: the committee noted the report and approved the budget for the year.";

    private Committee committee() { return Committee.create(tenant, "COM-1", "Risk Committee", CommitteeType.RISK, null, 3, 2, 12, alice, "Alice"); }

    private CommitteeMeeting held(int qualifying, int quorum) {
        CommitteeMeeting m = CommitteeMeeting.schedule(tenant, committeeId, today.minusDays(1), "Q2 meeting", null);
        m.hold(List.of("a", "b"), qualifying, quorum, today);
        return m;
    }

    @Test @DisplayName("Committee: validates its settings and cannot be edited or reviewed once dissolved")
    void committeeRules() {
        assertThrows(IllegalArgumentException.class, () -> Committee.create(tenant, "C", " ", CommitteeType.BOARD, null, 3, 2, 12, alice, "A"));
        assertThrows(IllegalArgumentException.class, () -> Committee.create(tenant, "C", "X", null, null, 3, 2, 12, alice, "A"));
        assertThrows(IllegalArgumentException.class, () -> Committee.create(tenant, "C", "X", CommitteeType.BOARD, null, 13, 2, 12, alice, "A"));
        assertThrows(IllegalArgumentException.class, () -> Committee.create(tenant, "C", "X", CommitteeType.BOARD, null, 3, 0, 12, alice, "A"));
        Committee c = committee();
        assertThrows(IllegalArgumentException.class, () -> c.reviewCharter(today.plusDays(1), today));
        c.reviewCharter(today, today);
        assertEquals(today, c.getLastCharterReview());
        assertThrows(IllegalArgumentException.class, () -> c.dissolve("short"));
        c.dissolve("The board folded this committee into audit");
        assertThrows(IllegalStateException.class, () -> c.update("X", CommitteeType.RISK, null, 3, 2, 12));
        assertThrows(IllegalStateException.class, () -> c.dissolve(TXT));
    }

    @Test @DisplayName("Member: observers do not count for quorum; ended members do not count; end date is validated")
    void member() {
        CommitteeMember obs = CommitteeMember.add(tenant, committeeId, "Obs", null, MemberRole.OBSERVER, today.minusDays(10), today);
        CommitteeMember mem = CommitteeMember.add(tenant, committeeId, "Mem", null, MemberRole.MEMBER, today.minusDays(10), today);
        assertFalse(obs.countsForQuorum());
        assertTrue(mem.countsForQuorum());
        assertThrows(IllegalArgumentException.class, () -> mem.end(today.minusDays(11), today));
        assertThrows(IllegalArgumentException.class, () -> mem.end(today.plusDays(1), today));
        mem.end(today, today);
        assertFalse(mem.countsForQuorum());
        assertThrows(IllegalStateException.class, () -> mem.end(today, today));
        assertThrows(IllegalArgumentException.class, () -> CommitteeMember.add(tenant, committeeId, "", null, MemberRole.MEMBER, today, today));
        assertThrows(IllegalArgumentException.class, () -> CommitteeMember.add(tenant, committeeId, "X", null, MemberRole.MEMBER, today.plusDays(1), today));
    }

    @Test @DisplayName("Meeting: cannot be held before its date, needs attendance, and works out quorum")
    void hold() {
        CommitteeMeeting future = CommitteeMeeting.schedule(tenant, committeeId, today.plusDays(5), "Later", null);
        assertThrows(IllegalStateException.class, () -> future.hold(List.of("a"), 1, 1, today));
        CommitteeMeeting m = CommitteeMeeting.schedule(tenant, committeeId, today, "Today", null);
        assertThrows(IllegalArgumentException.class, () -> m.hold(List.of(), 0, 2, today));
        m.hold(List.of("a", "b"), 2, 3, today);
        assertEquals(Boolean.FALSE, m.getQuorumMet());
        assertThrows(IllegalStateException.class, () -> m.hold(List.of("a"), 1, 1, today), "already held");
        assertEquals(Boolean.TRUE, held(2, 2).getQuorumMet());
    }

    @Test @DisplayName("Meeting: cancel needs a reason and only from scheduled; only a scheduled meeting is editable")
    void cancel() {
        CommitteeMeeting m = CommitteeMeeting.schedule(tenant, committeeId, today.plusDays(2), "Later", null);
        assertThrows(IllegalArgumentException.class, () -> m.cancel("no"));
        m.update(today.plusDays(3), "Moved", "Agenda");
        m.cancel("Chair is unavailable that week");
        assertEquals(MeetingStatus.CANCELLED, m.getStatus());
        assertThrows(IllegalStateException.class, () -> m.update(today, "X", null));
        assertThrows(IllegalStateException.class, () -> m.cancel(TXT));
    }

    @Test @DisplayName("Minutes: only for a held meeting; approver cannot be the author; approved minutes are immutable")
    void minutes() {
        CommitteeMeeting scheduled = CommitteeMeeting.schedule(tenant, committeeId, today, "Today", null);
        assertThrows(IllegalStateException.class, () -> scheduled.recordMinutes(MIN, alice, "Alice"));
        CommitteeMeeting m = held(2, 2);
        assertThrows(IllegalArgumentException.class, () -> m.recordMinutes("too short", alice, "Alice"));
        assertThrows(IllegalStateException.class, () -> m.approveMinutes(bob, "Bob", today), "nothing to approve yet");
        m.recordMinutes(MIN, alice, "Alice");
        assertThrows(IllegalStateException.class, () -> m.approveMinutes(alice, "Alice", today));
        m.approveMinutes(bob, "Bob", today);
        assertEquals(MinutesStatus.APPROVED, m.getMinutesStatus());
        assertThrows(IllegalStateException.class, () -> m.recordMinutes(MIN, bob, "Bob"));
        assertFalse(m.canRecordResolutions(), "no new decisions after approval");
    }

    @Test @DisplayName("Resolutions can be recorded only with a quorum")
    void resolutionsNeedQuorum() {
        assertTrue(held(2, 2).canRecordResolutions());
        assertFalse(held(1, 2).canRecordResolutions());
        assertFalse(CommitteeMeeting.schedule(tenant, committeeId, today, "T", null).canRecordResolutions());
    }

    @Test @DisplayName("Resolution: an action needs owner and a due date not before the meeting; a decision carries neither")
    void resolution() {
        UUID mid = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> CommitteeResolution.create(tenant, mid, committeeId, ResolutionKind.ACTION, TXT, null, today, today));
        assertThrows(IllegalArgumentException.class, () -> CommitteeResolution.create(tenant, mid, committeeId, ResolutionKind.ACTION, TXT, "Bob", null, today));
        assertThrows(IllegalArgumentException.class, () -> CommitteeResolution.create(tenant, mid, committeeId, ResolutionKind.ACTION, TXT, "Bob", today.minusDays(1), today));
        assertThrows(IllegalArgumentException.class, () -> CommitteeResolution.create(tenant, mid, committeeId, ResolutionKind.DECISION, "short", null, null, today));
        CommitteeResolution d = CommitteeResolution.create(tenant, mid, committeeId, ResolutionKind.DECISION, TXT, "ignored", today, today);
        assertNull(d.getOwnerName());
        assertNull(d.getDueDate());
        assertThrows(IllegalStateException.class, () -> d.complete(today, null, today));
    }

    @Test @DisplayName("Action: complete once, not in the future; a completed action cannot be edited")
    void complete() {
        CommitteeResolution a = CommitteeResolution.create(tenant, UUID.randomUUID(), committeeId, ResolutionKind.ACTION, TXT, "Bob", today.plusDays(7), today);
        assertThrows(IllegalArgumentException.class, () -> a.complete(today.plusDays(1), null, today));
        a.complete(today, "Done and filed", today);
        assertEquals(ResolutionStatus.DONE, a.getStatus());
        assertThrows(IllegalStateException.class, () -> a.complete(today, null, today));
        assertThrows(IllegalStateException.class, () -> a.update(ResolutionKind.ACTION, TXT, "Bob", today, today));
    }
}
