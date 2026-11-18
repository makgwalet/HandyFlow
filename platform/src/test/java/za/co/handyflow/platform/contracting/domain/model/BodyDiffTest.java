package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BodyDiffTest {

    static final String BASE = "<h1>Agreement</h1><p>Clause one.</p><p>Clause two.</p><p>Clause three.</p>";

    @Test
    void identicalBodiesHaveNoHunks() {
        assertEquals(0, BodyDiff.hunkCount(BodyDiff.diff(BASE, BASE)));
        assertEquals(0, BodyDiff.hunkCount(BodyDiff.diff(BASE, "<h1>Agreement</h1>\n<p>Clause   one.</p><p>Clause two.</p><p>Clause three.</p>")));  // whitespace only
    }

    @Test
    void changedParagraphIsOneHunkWithRemovedAndAdded() {
        String proposed = "<h1>Agreement</h1><p>Clause one.</p><p>Clause two, amended.</p><p>Clause three.</p>";
        List<BodyDiff.Segment> segs = BodyDiff.diff(BASE, proposed);
        assertEquals(1, BodyDiff.hunkCount(segs));
        BodyDiff.Segment h = segs.stream().filter(s -> s.type().equals(BodyDiff.HUNK)).findFirst().orElseThrow();
        assertEquals(List.of("<p>Clause two.</p>"), h.removed());
        assertEquals(List.of("<p>Clause two, amended.</p>"), h.added());
    }

    @Test
    void insertionsAndDeletionsAreSeparateHunks() {
        String proposed = "<h1>Agreement</h1><p>Clause one.</p><p>Clause three.</p><p>New clause four.</p>";
        List<BodyDiff.Segment> segs = BodyDiff.diff(BASE, proposed);
        assertEquals(2, BodyDiff.hunkCount(segs));
        assertEquals(List.of("<p>Clause two.</p>"), segs.stream().filter(s -> s.hunkId() == 0).findFirst().orElseThrow().removed());
        assertEquals(List.of("<p>New clause four.</p>"), segs.stream().filter(s -> s.hunkId() == 1).findFirst().orElseThrow().added());
    }

    @Test
    void mergeAcceptingEverythingGivesTheProposal() {
        String proposed = "<h1>Agreement</h1><p>Clause one.</p><p>Clause three.</p><p>New clause four.</p>";
        assertEquals(proposed, BodyDiff.merge(BASE, proposed, Set.of(0, 1)));
    }

    @Test
    void mergeAcceptingNothingKeepsTheCurrentText() {
        String proposed = "<h1>Agreement</h1><p>Clause one.</p><p>Clause three.</p><p>New clause four.</p>";
        assertEquals(BASE, BodyDiff.merge(BASE, proposed, Set.of()));
    }

    @Test
    void mergeCanAcceptOneChangeAndRejectAnother() {
        String proposed = "<h1>Agreement</h1><p>Clause one.</p><p>Clause three.</p><p>New clause four.</p>";
        // hunk 0 removes clause two, hunk 1 adds clause four: accept only the addition
        assertEquals("<h1>Agreement</h1><p>Clause one.</p><p>Clause two.</p><p>Clause three.</p><p>New clause four.</p>",
                BodyDiff.merge(BASE, proposed, Set.of(1)));
    }

    @Test
    void emptyAndNullBodiesAreHandled() {
        assertEquals(1, BodyDiff.hunkCount(BodyDiff.diff(null, "<p>x</p>")));
        assertEquals(1, BodyDiff.hunkCount(BodyDiff.diff("<p>x</p>", "")));
        assertEquals(0, BodyDiff.hunkCount(BodyDiff.diff("", null)));
    }

    @Test
    void tablesAndListsAreSingleBlocks() {
        String a = "<p>Intro</p><table><tbody><tr><td>1</td></tr></tbody></table>";
        String b = "<p>Intro</p><table><tbody><tr><td>2</td></tr></tbody></table>";
        assertEquals(1, BodyDiff.hunkCount(BodyDiff.diff(a, b)));
    }
}
