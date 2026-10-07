package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.CompetencyFacts;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.Input;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.Item;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.Result;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.ScreeningFacts;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.State;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Deployment readiness rules: what counts as met, what blocks, and the percentage. */
class GuardReadinessCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate FAR = TODAY.plusYears(1);

    private static ScreeningFacts pass(String type, LocalDate due, int evidence) {
        return new ScreeningFacts(type, "PASS", TODAY.minusMonths(1), due, Instant.parse("2026-09-01T00:00:00Z"), evidence, null);
    }

    private static Input input(String psira, LocalDate psiraExpiry, List<ScreeningFacts> s, String... docs) {
        return new Input(TODAY, psira, psiraExpiry, s, Set.of(docs));
    }

    private static Item item(Result r, String key) { return r.items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow(); }

    private static List<ScreeningFacts> allThree() {
        return List.of(pass("CRIMINAL_RECORD_CHECK", FAR, 2), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
    }

    @Test @DisplayName("Ready at 100% when every required check is met")
    void ready() {
        Result r = GuardReadinessCalculator.calculate(input("123", FAR, allThree(), "ID_COPY"));
        assertThat(r.percent()).isEqualTo(100);
        assertThat(r.ready()).isTrue();
        assertThat(r.reasons()).isEmpty();
    }

    @Test @DisplayName("Nothing on file is 0% and lists every gap")
    void empty() {
        Result r = GuardReadinessCalculator.calculate(input(null, null, List.of()));
        assertThat(r.percent()).isZero();
        assertThat(r.ready()).isFalse();
        assertThat(r.reasons()).contains("PSiRA registration: no PSiRA number on file", "Criminal record check: not on file");
    }

    @Test @DisplayName("An expired criminal check is named with how long ago it expired, and the percentage drops")
    void expired() {
        var s = List.of(pass("CRIMINAL_RECORD_CHECK", TODAY.minusDays(8), 1), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
        Result r = GuardReadinessCalculator.calculate(input("123", FAR, s, "ID_COPY"));
        assertThat(r.ready()).isFalse();
        assertThat(r.percent()).isEqualTo(80);
        assertThat(item(r, "CRIMINAL_RECORD_CHECK").state()).isEqualTo(State.EXPIRED);
        assertThat(r.reasons()).containsExactly("Criminal record check: expired 8 days ago");
    }

    @Test @DisplayName("A pass with no evidence file is incomplete, not met")
    void noEvidence() {
        var s = List.of(pass("CRIMINAL_RECORD_CHECK", FAR, 0), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
        Result r = GuardReadinessCalculator.calculate(input("123", FAR, s, "ID_COPY"));
        assertThat(item(r, "CRIMINAL_RECORD_CHECK").state()).isEqualTo(State.INCOMPLETE);
        assertThat(r.ready()).isFalse();
    }

    @Test @DisplayName("Only the newest record of a type is judged, so a renewal replaces an old failure")
    void newestWins() {
        var old = new ScreeningFacts("DRUG_TEST", "FAIL", TODAY.minusYears(2), null, Instant.parse("2024-01-01T00:00:00Z"), 1, null);
        var s = List.of(old, pass("CRIMINAL_RECORD_CHECK", FAR, 1), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
        assertThat(GuardReadinessCalculator.calculate(input("123", FAR, s, "ID_COPY")).ready()).isTrue();
    }

    @Test @DisplayName("Pending, failed and not-cleared results are not met")
    void notMet() {
        var pending = new ScreeningFacts("DRUG_TEST", "PENDING", null, null, Instant.parse("2026-10-01T00:00:00Z"), 0, null);
        var failed = new ScreeningFacts("REFERENCE_CHECK", "FAIL", TODAY, null, Instant.parse("2026-10-01T00:00:00Z"), 1, null);
        var notCleared = new ScreeningFacts("CRIMINAL_RECORD_CHECK", "PASS", TODAY, FAR, Instant.parse("2026-10-01T00:00:00Z"), 1, "NOT_CLEARED");
        Result r = GuardReadinessCalculator.calculate(input("123", FAR, List.of(pending, failed, notCleared), "ID_COPY"));
        assertThat(item(r, "DRUG_TEST").state()).isEqualTo(State.PENDING);
        assertThat(item(r, "REFERENCE_CHECK").state()).isEqualTo(State.FAILED);
        assertThat(item(r, "CRIMINAL_RECORD_CHECK").state()).isEqualTo(State.FAILED);
        assertThat(r.percent()).isEqualTo(40); // PSiRA and ID copy only
        assertThat(r.reasons().get(0)).matches(".*(: failed|: not cleared by the reviewer)"); // worst first: a failure leads
        assertThat(r.reasons().get(r.reasons().size() - 1)).isEqualTo("Drug test: result still pending");
    }

    @Test @DisplayName("Renewal due within 30 days still counts as met but is flagged expiring")
    void expiring() {
        var s = List.of(pass("CRIMINAL_RECORD_CHECK", TODAY.plusDays(30), 1), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
        Result r = GuardReadinessCalculator.calculate(input("123", TODAY.plusDays(5), s, "ID_COPY"));
        assertThat(item(r, "CRIMINAL_RECORD_CHECK").state()).isEqualTo(State.EXPIRING);
        assertThat(item(r, "PSIRA").detail()).isEqualTo("expires in 5 days");
        assertThat(r.ready()).isTrue();
    }

    @Test @DisplayName("Day 31 is not yet flagged")
    void dayThirtyOne() {
        var s = List.of(pass("CRIMINAL_RECORD_CHECK", TODAY.plusDays(31), 1), pass("REFERENCE_CHECK", null, 1), pass("DRUG_TEST", FAR, 1));
        assertThat(item(GuardReadinessCalculator.calculate(input("123", FAR, s, "ID_COPY")), "CRIMINAL_RECORD_CHECK").state()).isEqualTo(State.MET);
    }

    @Test @DisplayName("A failed optional screening blocks readiness without changing the percentage; a missing one does not")
    void optional() {
        var poly = new ScreeningFacts("POLYGRAPH", "FAIL", TODAY, null, Instant.parse("2026-10-01T00:00:00Z"), 1, null);
        var all = new java.util.ArrayList<>(allThree()); all.add(poly);
        Result r = GuardReadinessCalculator.calculate(input("123", FAR, all, "ID_COPY"));
        assertThat(r.percent()).isEqualTo(100);
        assertThat(r.ready()).isFalse();
        assertThat(GuardReadinessCalculator.calculate(input("123", FAR, allThree(), "ID_COPY")).ready()).isTrue();
    }

    @Test @DisplayName("PSiRA with no expiry date is incomplete")
    void psiraNoExpiry() {
        Result r = GuardReadinessCalculator.calculate(input("123", null, allThree(), "ID_COPY"));
        assertThat(item(r, "PSIRA").state()).isEqualTo(State.INCOMPLETE);
        assertThat(r.percent()).isEqualTo(80);
    }

    // ── Competencies ──────────────────────────────────────────────────────────

    private static CompetencyFacts comp(String id, boolean required, LocalDate expiry, int evidence, boolean verified) {
        return new CompetencyFacts(id, "FIRST_AID", null, required, expiry, evidence, verified);
    }

    private static Input withComps(CompetencyFacts... c) {
        return new Input(TODAY, "123", FAR, allThree(), Set.of("ID_COPY"), List.of(c));
    }

    @Test @DisplayName("A competency is met only when in date, with a certificate, and verified")
    void competencyStates() {
        assertThat(GuardReadinessCalculator.competency(comp("a", true, FAR, 1, true), TODAY).state()).isEqualTo(State.MET);
        assertThat(GuardReadinessCalculator.competency(comp("a", true, null, 1, true), TODAY).detail()).isEqualTo("verified, no expiry");
        assertThat(GuardReadinessCalculator.competency(comp("a", true, FAR, 0, true), TODAY).state()).isEqualTo(State.INCOMPLETE);
        assertThat(GuardReadinessCalculator.competency(comp("a", true, FAR, 1, false), TODAY).state()).isEqualTo(State.UNVERIFIED);
        var expired = GuardReadinessCalculator.competency(comp("a", true, TODAY.minusDays(3), 1, true), TODAY);
        assertThat(expired.state()).isEqualTo(State.EXPIRED);
        assertThat(expired.detail()).isEqualTo("expired 3 days ago");
        assertThat(GuardReadinessCalculator.competency(comp("a", true, TODAY.plusDays(30), 1, true), TODAY).state()).isEqualTo(State.EXPIRING);
        assertThat(GuardReadinessCalculator.competency(comp("a", true, TODAY.plusDays(31), 1, true), TODAY).state()).isEqualTo(State.MET);
    }

    @Test @DisplayName("Expiry wins over a missing certificate so the supervisor sees it has lapsed")
    void expiredBeatsIncomplete() {
        assertThat(GuardReadinessCalculator.competency(comp("a", true, TODAY.minusDays(1), 0, false), TODAY).state()).isEqualTo(State.EXPIRED);
    }

    @Test @DisplayName("A required competency counts towards the percentage and names the reason")
    void requiredCounts() {
        Result r = GuardReadinessCalculator.calculate(withComps(comp("a", true, TODAY.minusDays(8), 1, true)));
        assertThat(r.percent()).isEqualTo(83); // 5 of 6 required items
        assertThat(r.ready()).isFalse();
        assertThat(r.reasons()).containsExactly("First aid: expired 8 days ago");
    }

    @Test @DisplayName("An optional competency is listed but never blocks readiness or changes the percentage")
    void optionalNeverBlocks() {
        Result r = GuardReadinessCalculator.calculate(withComps(comp("a", false, TODAY.minusDays(8), 1, true), comp("b", false, FAR, 0, false)));
        assertThat(r.percent()).isEqualTo(100);
        assertThat(r.ready()).isTrue();
        assertThat(r.reasons()).isEmpty();
        assertThat(r.items().stream().filter(i -> i.key().startsWith("COMPETENCY:")).count()).isEqualTo(2);
    }

    @Test @DisplayName("A custom title is used as the label")
    void title() {
        var item = GuardReadinessCalculator.competency(new CompetencyFacts("x", "OTHER", "Armed response", true, FAR, 1, true), TODAY);
        assertThat(item.label()).isEqualTo("Armed response");
        assertThat(item.key()).isEqualTo("COMPETENCY:x");
    }
}
