package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TriggerClosureRulesTest {
    private static List<String> run(Collection<String> started, Map<String, List<String>> graph) {
        return TriggerClosureRules.unstarted(started, c -> graph.get(c));
    }

    @Test void nothingStartedMeansNothingOpened() { assertEquals(List.of(), run(List.of(), Map.of("A", List.of("B")))); }

    @Test void aGroupOpenedByAStartedGroupIsReported() { assertEquals(List.of("B"), run(List.of("A"), Map.of("A", List.of("B")))); }

    @Test void aGroupThatWasStartedIsNotReported() { assertEquals(List.of(), run(List.of("A", "B"), Map.of("A", List.of("B")))); }

    @Test void triggersAreFollowedThroughOpenedGroups() {
        assertEquals(List.of("B", "C"), run(List.of("A"), Map.of("A", List.of("B"), "B", List.of("C"))));
    }

    @Test void aLoopEnds() {
        assertEquals(List.of("B"), run(List.of("A"), Map.of("A", List.of("B"), "B", List.of("A", "B"))));
    }

    @Test void aGroupOpenedTwiceIsReportedOnce() {
        assertEquals(List.of("C"), run(List.of("A", "B"), Map.of("A", List.of("C"), "B", List.of("C"))));
    }

    @Test void unknownGroupsAndNullsAreIgnored() {
        Map<String, List<String>> g = new HashMap<>(); g.put("A", Arrays.asList("B", null));
        assertEquals(List.of("B"), run(List.of("A"), g));
    }
}
