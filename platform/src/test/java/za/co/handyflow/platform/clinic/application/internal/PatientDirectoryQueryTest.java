package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatientDirectoryQueryTest {

    static final Instant S = Instant.parse("2026-10-07T22:00:00Z"), E = Instant.parse("2026-10-08T22:00:00Z");

    private static PatientDirectoryQuery.Built b(String view, String search, boolean arch, UUID doc, List<UUID> fu) {
        return PatientDirectoryQuery.build(view, search, arch, doc, fu, S, E);
    }

    @Test void unknownViewFallsBackToAll() {
        assertEquals("ALL", PatientDirectoryQuery.normaliseView("whatever"));
        assertEquals("RECENT", PatientDirectoryQuery.normaliseView(" recent "));
        assertEquals("ALL", PatientDirectoryQuery.normaliseView(null));
    }

    @Test void allHidesArchivedUnlessAsked() {
        assertTrue(b("ALL", null, false, null, null).where().contains("archived_at IS NULL"));
        assertFalse(b("ALL", null, true, null, null).where().contains("archived_at IS NULL"));
    }

    @Test void searchBindsFourEscapedPatternsAndNeverInlinesText() {
        var q = b("ALL", "  50%_Ada ", false, null, null);
        assertEquals(4, q.params().size());
        assertEquals("%50\\%\\_ada%", q.params().get(0));
        assertFalse(q.where().contains("Ada"));
    }

    @Test void recentOrdersByLatestVisitAndNeverSeenByNewestRegistration() {
        assertTrue(b("RECENT", null, false, null, null).where().contains("v.last_at IS NOT NULL"));
        assertTrue(b("RECENT", null, false, null, null).orderBy().startsWith("v.last_at DESC"));
        assertTrue(b("NEVER_SEEN", null, false, null, null).where().contains("v.cnt IS NULL"));
        assertTrue(b("NEVER_SEEN", null, false, null, null).orderBy().startsWith("p.created_at DESC"));
    }

    @Test void todayBindsTheClinicDayTwice() {
        var q = b("TODAY", null, false, null, null);
        assertEquals(4, q.params().size());
    }

    @Test void mineNeedsAPractitioner() {
        assertTrue(b("MINE", null, false, null, null).where().contains("1 = 0"));
        UUID d = UUID.randomUUID();
        var q = b("MINE", null, false, d, null);
        assertEquals(List.of(d), q.params());
    }

    @Test void followUpUsesTheOpenRecallPatientsOrNothing() {
        assertTrue(b("FOLLOW_UP", null, false, null, List.of()).where().contains("1 = 0"));
        UUID a = UUID.randomUUID(), c = UUID.randomUUID();
        var q = b("FOLLOW_UP", null, false, null, List.of(a, c));
        assertEquals(a + "," + c, q.params().get(0));
    }

    @Test void duplicatesGroupsNamesTogether() {
        var q = b("DUPLICATES", null, false, null, null);
        assertTrue(q.where().contains("d.cnt > 1"));
        assertTrue(q.orderBy().startsWith("lower(p.last_name)"));
    }

    @Test void clampsPageSize() {
        assertEquals(1, PatientDirectoryQuery.clampSize(0));
        assertEquals(100, PatientDirectoryQuery.clampSize(5000));
        assertEquals(25, PatientDirectoryQuery.clampSize(25));
    }
}
