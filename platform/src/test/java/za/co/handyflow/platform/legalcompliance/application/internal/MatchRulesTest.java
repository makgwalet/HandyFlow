package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.MatchRules.Candidate;
import za.co.handyflow.platform.legalcompliance.application.internal.MatchRules.Strength;
import za.co.handyflow.platform.legalcompliance.application.internal.MatchRules.Suggestion;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchRulesTest {

    private static Candidate c(String type, String ref, String label, String text) { return new Candidate(type, UUID.randomUUID(), ref, label, text); }

    private final List<Candidate> corpus = List.of(
            c("OBLIGATION", "POPIA", "Report a data breach to the Information Regulator", "notify regulator of personal information breach within reasonable time"),
            c("OBLIGATION", "OHSA", "Annual workplace safety inspection", "inspect machinery and record incidents"),
            c("CONTROL", "CTL-00001", "Access review for systems holding personal information", ""),
            c("POLICY", "POL-00001", "Fleet driver conduct policy", ""));

    @Test @DisplayName("Tokens drop short words, stop words and generic legal filler, and are lower-cased")
    void tokens() {
        Set<String> t = MatchRules.tokens("The Draft Regulations: Amendment of the POPIA Act, notice 2026!");
        assertTrue(t.contains("popia"));
        assertTrue(t.contains("2026"));
        assertFalse(t.contains("the"));
        assertFalse(t.contains("draft"));
        assertFalse(t.contains("act"));
        assertFalse(t.contains("regulations"));
        assertTrue(MatchRules.tokens(null).isEmpty());
    }

    @Test @DisplayName("Relevant items rank first, each lists the words that matched, and unrelated items are not suggested")
    void ranking() {
        List<Suggestion> out = MatchRules.suggest("New guidance on notifying the Information Regulator of a personal information breach", corpus, 10);
        assertFalse(out.isEmpty());
        assertEquals("Report a data breach to the Information Regulator", out.get(0).label());
        assertTrue(out.get(0).matchedTerms().contains("regulator"));
        assertTrue(out.stream().noneMatch(s -> s.label().contains("Fleet driver")));
        assertTrue(out.stream().noneMatch(s -> s.label().contains("workplace safety")));
    }

    @Test @DisplayName("Naming an item's reference is a strong signal on its own")
    void referenceMention() {
        List<Suggestion> out = MatchRules.suggest("OHSA amended again", corpus, 10);
        assertEquals("OHSA", out.get(0).reference());
        assertTrue(out.get(0).referenceMentioned());
        assertEquals(Strength.HIGH, out.get(0).strength());
        assertTrue(MatchRules.suggest("OHSAX unrelated", corpus, 10).stream().noneMatch(Suggestion::referenceMentioned), "whole-phrase match only");
    }

    @Test @DisplayName("Strength reflects how many words matched; a single weak word is LOW")
    void strength() {
        List<Suggestion> out = MatchRules.suggest("machinery", corpus, 10);
        assertEquals(1, out.size());
        assertEquals(Strength.LOW, out.get(0).strength());
    }

    @Test @DisplayName("The limit is respected, and an empty corpus or empty text gives nothing")
    void limitsAndEmpty() {
        assertEquals(1, MatchRules.suggest("personal information regulator breach", corpus, 1).size());
        assertTrue(MatchRules.suggest("anything", List.of(), 10).isEmpty());
        assertTrue(MatchRules.suggest("", corpus, 10).isEmpty());
        assertTrue(MatchRules.suggest("zebra giraffe", corpus, 10).isEmpty());
    }

    @Test @DisplayName("Rare words outweigh common ones")
    void idfWeighting() {
        List<Candidate> cs = List.of(c("OBLIGATION", "A", "Safety records safety", ""), c("OBLIGATION", "B", "Safety training safety", ""), c("OBLIGATION", "C", "Asbestos register safety", ""));
        List<Suggestion> out = MatchRules.suggest("asbestos safety", cs, 10);
        assertEquals("C", out.get(0).reference());
    }
}
