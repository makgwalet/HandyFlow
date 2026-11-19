package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules.Delta;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules.Kind;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules.Req;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeDetectionRulesTest {

    private Req req(String ref, String text) { return req(UUID.randomUUID(), null, ref, "Title " + ref, text, "PRIVACY", "ANNUAL", null, List.of(), List.of(), "Evidence"); }

    private Req req(UUID id, UUID prev, String ref, String title, String text, String cat, String rec, Integer min, List<String> ind, List<String> act, String ev) {
        return new Req(id, prev, ref, title, text, cat, rec, min, ind, act, ev);
    }

    private Delta only(List<Delta> ds, Kind k) { return ds.stream().filter(d -> d.kind() == k).findFirst().orElseThrow(); }

    @Test
    @DisplayName("identical requirements are UNCHANGED, even with different whitespace in the text")
    void unchanged() {
        var o = req("s9", "Process lawfully");
        var n = req(UUID.randomUUID(), o.id(), "s9", o.title(), "Process   lawfully\n", "PRIVACY", "ANNUAL", null, List.of(), List.of(), "Evidence");
        var ds = ChangeDetectionRules.diff(List.of(o), List.of(n));
        assertEquals(1, ds.size());
        assertEquals(Kind.UNCHANGED, ds.get(0).kind());
        assertNull(ds.get(0).summary());
    }

    @Test
    @DisplayName("matching falls back to the reference, case-insensitively, when there is no previous id")
    void matchByReference() {
        var o = req("S9(1)", "Text");
        var n = req("s9(1)", "Text");
        assertEquals(Kind.UNCHANGED, ChangeDetectionRules.diff(List.of(o), List.of(n)).get(0).kind());
    }

    @Test
    @DisplayName("a changed text is CHANGED and says what changed")
    void changedText() {
        var o = req("s9", "Old wording");
        var n = req(UUID.randomUUID(), o.id(), "s9", o.title(), "New wording", "PRIVACY", "ANNUAL", null, List.of(), List.of(), "Evidence");
        Delta d = ChangeDetectionRules.diff(List.of(o), List.of(n)).get(0);
        assertEquals(Kind.CHANGED, d.kind());
        assertEquals("Requirement text changed", d.summary());
    }

    @Test
    @DisplayName("frequency, threshold, scope and evidence changes are each reported")
    void structuredChanges() {
        var o = req(UUID.randomUUID(), null, "s1", "T", "X", "PRIVACY", "ANNUAL", 50, List.of("A"), List.of("x"), "E1");
        var n = req(UUID.randomUUID(), o.id(), "s1", "T", "X", "PRIVACY", "QUARTERLY", 10, List.of("B"), List.of("y"), "E2");
        String s = ChangeDetectionRules.diff(List.of(o), List.of(n)).get(0).summary();
        assertTrue(s.contains("Frequency ANNUAL → QUARTERLY"));
        assertTrue(s.contains("Employee threshold 50 → 10"));
        assertTrue(s.contains("Industries"));
        assertTrue(s.contains("Activities"));
        assertTrue(s.contains("Evidence expectation changed"));
    }

    @Test
    @DisplayName("industry order does not count as a change")
    void setOrderIgnored() {
        var o = req(UUID.randomUUID(), null, "s1", "T", "X", "PRIVACY", "ANNUAL", null, List.of("A", "B"), List.of(), "E");
        var n = req(UUID.randomUUID(), o.id(), "s1", "T", "X", "PRIVACY", "ANNUAL", null, List.of("B", "A"), List.of(), "E");
        assertEquals(Kind.UNCHANGED, ChangeDetectionRules.diff(List.of(o), List.of(n)).get(0).kind());
    }

    @Test
    @DisplayName("new requirements are ADDED and missing ones are REMOVED")
    void addedAndRemoved() {
        var kept = req("s1", "A");
        var gone = req("s2", "B");
        var fresh = req("s3", "C");
        var keptNew = req(UUID.randomUUID(), kept.id(), "s1", kept.title(), "A", "PRIVACY", "ANNUAL", null, List.of(), List.of(), "Evidence");
        var ds = ChangeDetectionRules.diff(List.of(kept, gone), List.of(keptNew, fresh));
        assertEquals(3, ds.size());
        assertEquals("s3", only(ds, Kind.ADDED).reference());
        assertEquals(gone.id(), only(ds, Kind.REMOVED).oldId());
        assertNull(only(ds, Kind.REMOVED).newId());
    }

    @Test
    @DisplayName("an old requirement is matched at most once")
    void matchedOnce() {
        var o = req("s1", "A");
        var n1 = req(UUID.randomUUID(), o.id(), "s1", o.title(), "A", "PRIVACY", "ANNUAL", null, List.of(), List.of(), "Evidence");
        var n2 = req("S1", "A");
        var ds = ChangeDetectionRules.diff(List.of(o), List.of(n1, n2));
        assertEquals(1, ds.stream().filter(d -> d.kind() == Kind.UNCHANGED).count());
        assertEquals(1, ds.stream().filter(d -> d.kind() == Kind.ADDED).count());
    }

    @Test
    @DisplayName("an empty new version removes everything; an empty old version adds everything")
    void emptySides() {
        var r = req("s1", "A");
        assertEquals(Kind.REMOVED, ChangeDetectionRules.diff(List.of(r), List.of()).get(0).kind());
        assertEquals(Kind.ADDED, ChangeDetectionRules.diff(List.of(), List.of(r)).get(0).kind());
    }
}
