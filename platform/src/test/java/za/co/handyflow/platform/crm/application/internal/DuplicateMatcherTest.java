package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.DuplicateMatcher.Candidate;
import za.co.handyflow.platform.crm.application.internal.DuplicateMatcher.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateMatcherTest {

    private static UUID id(int n) { return new UUID(0, n); }

    private static Candidate c(int n, String name, String phone, String tax) { return new Candidate(id(n), name, phone, tax); }

    private static List<Group> run(List<Candidate> cs, Set<String> dismissed) {
        return DuplicateMatcher.find(cs, dismissed,
                (a, b) -> CustomerNameSimilarityChecker.jaroWinkler(a, b) >= CustomerNameSimilarityChecker.THRESHOLD);
    }

    @Test
    void normalisingNamesPhonesAndTaxNumbers() {
        assertThat(DuplicateMatcher.normaliseName("Tau Mining (Pty) Ltd")).isEqualTo("tau mining");
        assertThat(DuplicateMatcher.normaliseName("Smith & Sons CC")).isEqualTo("smith sons");
        assertThat(DuplicateMatcher.normaliseName(null)).isEmpty();
        assertThat(DuplicateMatcher.normalisePhone("082 123 4567")).isEqualTo("821234567");
        assertThat(DuplicateMatcher.normalisePhone("+27 82 123 4567")).isEqualTo("821234567");
        assertThat(DuplicateMatcher.normalisePhone("12345")).isNull();
        assertThat(DuplicateMatcher.normaliseTax("4123 456 789")).isEqualTo("4123456789");
        assertThat(DuplicateMatcher.normaliseTax("123")).isNull();
    }

    @Test
    void sameNameIgnoringCompanySuffixes() {
        var g = run(List.of(c(1, "Tau Mining Ltd", null, null), c(2, "Tau Mining (Pty) Ltd", null, null), c(3, "Bravo Foods", null, null)), Set.of());
        assertThat(g).hasSize(1);
        assertThat(g.get(0).ids()).hasSize(2);
        assertThat(g.get(0).reasons()).containsExactly(DuplicateMatcher.SAME_NAME);
    }

    @Test
    void samePhoneWithDifferentNames() {
        var g = run(List.of(c(1, "Acme", "082 123 4567", null), c(2, "Totally Different", "+27821234567", null)), Set.of());
        assertThat(g).hasSize(1);
        assertThat(g.get(0).reasons()).containsExactly(DuplicateMatcher.SAME_PHONE);
    }

    @Test
    void sameVatNumberIsListedFirst() {
        var g = run(List.of(c(1, "Acme Traders", null, "4123456789"), c(2, "Acme Trading Co", null, "4123 456 789")), Set.of());
        assertThat(g).hasSize(1);
        assertThat(g.get(0).reasons().get(0)).isEqualTo(DuplicateMatcher.SAME_TAX);
    }

    @Test
    void aTypoIsVerySimilar() {
        var g = run(List.of(c(1, "Cape Harvest Wines", null, null), c(2, "Cape Harvest Winez", null, null)), Set.of());
        assertThat(g).hasSize(1);
        assertThat(g.get(0).reasons()).containsExactly(DuplicateMatcher.SIMILAR_NAME);
    }

    @Test
    void differentTradingNamesAreNotFlagged() {
        assertThat(run(List.of(c(1, "Tau Mining", null, null), c(2, "Tau Logistics", null, null)), Set.of())).isEmpty();
    }

    @Test
    void aDismissedPairDisappearsWhateverOrderItWasStored() {
        var two = List.of(c(1, "Tau Mining Ltd", null, null), c(2, "Tau Mining (Pty) Ltd", null, null));
        assertThat(run(two, Set.of(DuplicateMatcher.pairKey(id(2), id(1))))).isEmpty();
        assertThat(DuplicateMatcher.pairKey(id(1), id(2))).isEqualTo(DuplicateMatcher.pairKey(id(2), id(1)));
    }

    @Test
    void linkedRecordsJoinIntoOneGroup() {
        var g = run(List.of(c(1, "Acme", "0821234567", null), c(2, "Acme Ltd", null, null), c(3, "Other", "082 123 4567", null)), Set.of());
        assertThat(g).hasSize(1);
        assertThat(g.get(0).ids()).hasSize(3);
        assertThat(g.get(0).reasons()).containsExactly(DuplicateMatcher.SAME_PHONE, DuplicateMatcher.SAME_NAME);
    }

    @Test
    void aSwitchboardNumberSharedByManyIsNotEvidence() {
        String[] words = {"Zephyr", "Quartz", "Mohair", "Tundra", "Basalt", "Kestrel", "Orchid", "Lantern", "Pylon", "Saffron", "Granite", "Willow", "Falcon",
                "Cobalt", "Meadow", "Harbor", "Juniper", "Nimbus", "Rowan", "Sable", "Ember", "Vortex", "Thistle", "Umber", "Yarrow"};
        var many = new ArrayList<Candidate>();
        for (int i = 0; i < 25; i++) many.add(c(i + 1, words[i] + " " + words[(i * 7 + 3) % 25] + " Supplies", "011 555 0000", null));
        assertThat(run(many, Set.of())).isEmpty();
    }

    @Test
    void blankAndEmptyInputsMatchNothing() {
        assertThat(run(List.of(c(1, "", "", ""), c(2, "", "", "")), Set.of())).isEmpty();
        assertThat(run(List.of(), Set.of())).isEmpty();
    }
}
