package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterOfferRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant SENT = Instant.parse("2026-10-20T08:00:00Z");

    private static String st(Instant sent, String resp, LocalDate exp) { return RecruiterOfferRules.status(sent, resp, exp, TODAY); }

    @Test
    void statusFollowsTheLetterThenTheDate() {
        assertThat(st(null, "NONE", null)).isEqualTo("DRAFT");
        assertThat(st(SENT, "NONE", TODAY)).isEqualTo("AWAITING");
        assertThat(st(SENT, "NONE", TODAY.minusDays(1))).isEqualTo("EXPIRED");
        assertThat(st(SENT, "NONE", null)).isEqualTo("AWAITING");
        assertThat(st(SENT, "ACCEPTED", TODAY.minusDays(9))).isEqualTo("ACCEPTED");
        assertThat(st(null, "ACCEPTED", null)).isEqualTo("ACCEPTED");
    }

    @Test
    void expiryDefaultsAndLimits() {
        assertThat(RecruiterOfferRules.expiryOrDefault(null, TODAY)).isEqualTo(TODAY.plusDays(14));
        assertThat(RecruiterOfferRules.expiryProblem(TODAY.minusDays(1), TODAY)).contains("past");
        assertThat(RecruiterOfferRules.expiryProblem(TODAY.plusDays(91), TODAY)).contains("90");
        assertThat(RecruiterOfferRules.expiryProblem(TODAY.plusDays(90), TODAY)).isNull();
        assertThat(RecruiterOfferRules.expiryProblem(null, TODAY)).isNull();
    }

    @Test
    void sendingIsOnlyForDraftsAndLapsedOffers() {
        assertThat(RecruiterOfferRules.sendProblem("DRAFT")).isNull();
        assertThat(RecruiterOfferRules.sendProblem("EXPIRED")).isNull();
        assertThat(RecruiterOfferRules.sendProblem("AWAITING")).contains("already been sent");
        assertThat(RecruiterOfferRules.sendProblem("ACCEPTED")).contains("accepted");
        assertThat(RecruiterOfferRules.sendProblem("DECLINED")).contains("closed");
    }

    @Test
    void answers() {
        assertThat(RecruiterOfferRules.responseProblem("AWAITING", "accepted", null)).isNull();
        assertThat(RecruiterOfferRules.responseProblem("DRAFT", "ACCEPTED", null)).isNull();
        assertThat(RecruiterOfferRules.responseProblem("AWAITING", "DECLINED", " ")).contains("reason");
        assertThat(RecruiterOfferRules.responseProblem("AWAITING", "DECLINED", "Took another job")).isNull();
        assertThat(RecruiterOfferRules.responseProblem("AWAITING", "MAYBE", "x")).contains("Choose");
        assertThat(RecruiterOfferRules.responseProblem("EXPIRED", "ACCEPTED", null)).contains("lapsed");
        assertThat(RecruiterOfferRules.responseProblem("EXPIRED", "DECLINED", "No")).isNull();
        assertThat(RecruiterOfferRules.responseProblem("ACCEPTED", "DECLINED", "x")).contains("already been accepted");
        assertThat(RecruiterOfferRules.responseProblem("AWAITING", "DECLINED", "x".repeat(501))).contains("too long");
    }

    @Test
    void revising() {
        assertThat(RecruiterOfferRules.reviseProblem("DRAFT", null)).isNull();
        assertThat(RecruiterOfferRules.reviseProblem("AWAITING", " ")).contains("reason");
        assertThat(RecruiterOfferRules.reviseProblem("EXPIRED", "Raised salary")).isNull();
        assertThat(RecruiterOfferRules.reviseProblem("ACCEPTED", "x")).contains("cannot be changed");
        assertThat(RecruiterOfferRules.reviseProblem("WITHDRAWN", "x")).contains("closed");
    }

    @Test
    void hiringNeedsAnAcceptedOffer() {
        assertThat(RecruiterOfferRules.hireProblem("ACCEPTED")).isNull();
        for (String s : new String[]{"DRAFT", "AWAITING", "EXPIRED", "DECLINED"})
            assertThat(RecruiterOfferRules.hireProblem(s)).contains("accepted");
    }
}
