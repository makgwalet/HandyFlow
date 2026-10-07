package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllergyMatcherTest {

    private static AllergyMatcher.Fact fact(String allergen, String type, String severity) {
        return new AllergyMatcher.Fact(allergen, type, severity, null);
    }

    private final List<AllergyMatcher.Fact> penicillin = List.of(fact("Penicillin", "DRUG", "SEVERE"));

    @Test
    @DisplayName("matches by name regardless of case and punctuation")
    void matchesByName() {
        assertThat(AllergyMatcher.match("Penicillin V 250mg tablets", penicillin)).hasSize(1);
        assertThat(AllergyMatcher.match("PENICILLIN-V", penicillin)).hasSize(1);
        assertThat(AllergyMatcher.match("Penicillin", penicillin).get(0).summary()).isEqualTo("Penicillin (severe)");
    }

    @Test
    @DisplayName("does NOT know drug classes: amoxicillin is not matched to a penicillin allergy")
    void noClassKnowledge() {
        assertThat(AllergyMatcher.match("Amoxicillin 500mg", penicillin)).isEmpty();
    }

    @Test
    @DisplayName("an allergen written longer than the medicine's name still matches its first word")
    void allergenLonger() {
        assertThat(AllergyMatcher.match("Amoxicillin 500mg", List.of(fact("Amoxicillin tablets", "DRUG", null)))).hasSize(1);
    }

    @Test
    @DisplayName("food and environmental allergies are skipped; unknown type is checked")
    void typesSkipped() {
        assertThat(AllergyMatcher.match("Egg albumin", List.of(fact("Egg albumin", "FOOD", null)))).isEmpty();
        assertThat(AllergyMatcher.match("Dust mite", List.of(fact("Dust mite", "ENVIRONMENT", null)))).isEmpty();
        assertThat(AllergyMatcher.match("Aspirin 100mg", List.of(fact("aspirin", "UNKNOWN", "MILD")))).hasSize(1);
    }

    @Test
    @DisplayName("blank medicine, null list, very short allergen and unrelated names give no match")
    void edges() {
        assertThat(AllergyMatcher.match("  ", penicillin)).isEmpty();
        assertThat(AllergyMatcher.match("Aspirin", null)).isEmpty();
        assertThat(AllergyMatcher.match("Zinc sulphate", List.of(fact("Zn", "DRUG", null)))).isEmpty();
        assertThat(AllergyMatcher.match("Paracetamol 500mg", penicillin)).isEmpty();
    }
}
