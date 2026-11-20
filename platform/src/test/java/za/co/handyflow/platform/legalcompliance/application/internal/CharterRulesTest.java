package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.domain.model.ApprovalThreshold;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeCharterRules;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenMode;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CharterRulesTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final UUID C = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static CommitteeCharterRules v(int no, LocalDate effective, boolean casting, WrittenMode mode, boolean quorum, int pct) {
        return CommitteeCharterRules.record(T, C, no, null, effective.minusDays(5), effective, casting, mode, quorum, pct, "Adopted by the board", "me");
    }

    @Test @DisplayName("No versions: the earlier house rules apply")
    void none() {
        CharterRules.Applied a = CharterRules.current(List.of(), TODAY);
        assertFalse(a.recorded());
        assertEquals(CharterRules.NONE, a);
        assertEquals(75, a.specialPercent());
        assertTrue(a.quorumAfterRecusal());
        assertEquals(WrittenMode.EITHER, a.writtenMode());
    }

    @Test @DisplayName("The newest version already in force wins; a future version waits; the effective date itself counts")
    void current() {
        CommitteeCharterRules v1 = v(1, TODAY.minusDays(100), true, WrittenMode.EITHER, true, 75), v2 = v(2, TODAY, false, WrittenMode.UNANIMOUS, false, 80), v3 = v(3, TODAY.plusDays(1), true, WrittenMode.NOT_ALLOWED, true, 90);
        CharterRules.Applied a = CharterRules.current(List.of(v1, v3, v2), TODAY);
        assertEquals(2, a.versionNo());
        assertFalse(a.castingVote());
        assertEquals(80, a.specialPercent());
        assertEquals(1, CharterRules.current(List.of(v1, v2), TODAY.minusDays(1)).versionNo());
        assertEquals(3, CharterRules.current(List.of(v1, v2, v3), TODAY.plusDays(1)).versionNo());
        assertFalse(CharterRules.current(List.of(v2), TODAY.minusDays(1)).recorded(), "before the first version takes effect there is no charter");
    }

    @Test @DisplayName("Casting vote: the charter decides when there is one; the confirmation decides when there is not")
    void casting() {
        CharterRules.Applied yes = CharterRules.current(List.of(v(1, TODAY, true, WrittenMode.EITHER, true, 75)), TODAY), no = CharterRules.current(List.of(v(1, TODAY, false, WrittenMode.EITHER, true, 75)), TODAY);
        assertNull(CharterRules.castingVoteProblem(yes, false));
        assertNull(CharterRules.castingVoteProblem(yes, true));
        assertNotNull(CharterRules.castingVoteProblem(no, true));
        assertNotNull(CharterRules.castingVoteProblem(CharterRules.NONE, false));
        assertNull(CharterRules.castingVoteProblem(CharterRules.NONE, true));
    }

    @Test @DisplayName("Written resolutions: each mode allows exactly its thresholds")
    void written() {
        for (WrittenMode m : WrittenMode.values()) {
            CharterRules.Applied a = CharterRules.current(List.of(v(1, TODAY, true, m, true, 75)), TODAY);
            boolean u = CharterRules.writtenProblem(a, ApprovalThreshold.UNANIMOUS) == null, mj = CharterRules.writtenProblem(a, ApprovalThreshold.MAJORITY_OF_ALL) == null;
            assertEquals(m == WrittenMode.UNANIMOUS || m == WrittenMode.EITHER, u, m + " unanimous");
            assertEquals(m == WrittenMode.MAJORITY_OF_ALL || m == WrittenMode.EITHER, mj, m + " majority");
            assertEquals((u ? 1 : 0) + (mj ? 1 : 0), CharterRules.allowedThresholds(a).size(), m + " offered");
        }
        assertNull(CharterRules.writtenProblem(CharterRules.NONE, ApprovalThreshold.UNANIMOUS));
    }

    @Test @DisplayName("A version that changes nothing is recognised")
    void same() {
        CharterRules.Applied a = CharterRules.current(List.of(v(1, TODAY, true, WrittenMode.EITHER, true, 75)), TODAY);
        assertTrue(CharterRules.sameRules(a, true, WrittenMode.EITHER, true, 75));
        assertFalse(CharterRules.sameRules(a, false, WrittenMode.EITHER, true, 75));
        assertFalse(CharterRules.sameRules(a, true, WrittenMode.UNANIMOUS, true, 75));
        assertFalse(CharterRules.sameRules(a, true, WrittenMode.EITHER, false, 75));
        assertFalse(CharterRules.sameRules(a, true, WrittenMode.EITHER, true, 76));
        assertFalse(CharterRules.sameRules(CharterRules.NONE, true, WrittenMode.EITHER, true, 75), "recording the first version is never 'nothing'");
    }

    @Test @DisplayName("A version needs valid dates, a percentage from 51 to 100, a mode and a reason")
    void validation() {
        LocalDate d = TODAY;
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, null, d, d.minusDays(1), true, WrittenMode.EITHER, true, 75, "Adopted by the board", "me"));
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, null, d, d, true, WrittenMode.EITHER, true, 50, "Adopted by the board", "me"));
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, null, d, d, true, WrittenMode.EITHER, true, 101, "Adopted by the board", "me"));
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, null, d, d, true, null, true, 75, "Adopted by the board", "me"));
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, null, d, d, true, WrittenMode.EITHER, true, 75, "short", "me"));
        assertThrows(IllegalArgumentException.class, () -> CommitteeCharterRules.record(T, C, 1, "x".repeat(121), d, d, true, WrittenMode.EITHER, true, 75, "Adopted by the board", "me"));
        assertDoesNotThrow(() -> CommitteeCharterRules.record(T, C, 1, " ", d, d, true, WrittenMode.EITHER, true, 51, "Adopted by the board", "me"));
        assertNull(v(1, TODAY, true, WrittenMode.EITHER, true, 100).getCharterReference());
    }
}
