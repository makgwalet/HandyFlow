package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegulatoryRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Test @DisplayName("a web address must be http or https with no spaces, and is optional")
    void urls() {
        assertThat(RegulatoryRules.url(null)).isNull();
        assertThat(RegulatoryRules.url("  ")).isNull();
        assertThat(RegulatoryRules.url(" https://example.org/notice ")).isEqualTo("https://example.org/notice");
        assertThatThrownBy(() -> RegulatoryRules.url("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegulatoryRules.url("https://a b.org")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegulatoryRules.url("ftp://example.org")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("kinds and change types are checked, and kind defaults to other")
    void vocabulary() {
        assertThat(RegulatoryRules.kind(null)).isEqualTo("OTHER");
        assertThat(RegulatoryRules.kind(" notice ")).isEqualTo("NOTICE");
        assertThatThrownBy(() -> RegulatoryRules.kind("RUMOUR")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RegulatoryRules.changeType("amended")).isEqualTo("AMENDED");
        assertThatThrownBy(() -> RegulatoryRules.changeType("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("dates: not published in the future, not effective more than ten years away")
    void dates() {
        RegulatoryRules.checkDates(TODAY, TODAY.plusYears(1), TODAY);
        RegulatoryRules.checkDates(null, null, TODAY);
        assertThatThrownBy(() -> RegulatoryRules.checkDates(TODAY.plusDays(1), null, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegulatoryRules.checkDates(null, TODAY.plusYears(11), TODAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("confirming needs an effective date, except for proposals and guidance")
    void confirming() {
        assertThat(RegulatoryRules.confirmProblem("AMENDED", null)).isNotNull();
        assertThat(RegulatoryRules.confirmProblem("AMENDED", TODAY)).isNull();
        assertThat(RegulatoryRules.confirmProblem("PROPOSED", null)).isNull();
        assertThat(RegulatoryRules.confirmProblem("GUIDANCE", null)).isNull();
    }

    @Test @DisplayName("the person who raised a change needs a reason of 15 characters to review it; someone else does not")
    void exception() {
        UUID raiser = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertThat(RegulatoryRules.exception(raiser, other, null)).isNull();
        assertThatThrownBy(() -> RegulatoryRules.exception(raiser, raiser, "too short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RegulatoryRules.exception(raiser, raiser, "  Only admin on shift today  ")).isEqualTo("Only admin on shift today");
        assertThat(RegulatoryRules.exception(null, other, null)).isNull();
    }

    @Test @DisplayName("what can be done to an impact depends on where it is")
    void impactActions() {
        assertThat(RegulatoryRules.impactActions("OPEN")).containsExactly("NOTICE_GIVEN", "NO_ACTION", "DONE");
        assertThat(RegulatoryRules.impactActions("NOTICE_GIVEN")).containsExactly("DONE", "REOPEN");
        assertThat(RegulatoryRules.impactActions("DONE")).containsExactly("REOPEN");
        assertThat(RegulatoryRules.impactLive("OPEN")).isTrue();
        assertThat(RegulatoryRules.impactLive("NO_ACTION")).isFalse();
    }

    @Test @DisplayName("an impact is urgent once the change is a week away, and low with no date")
    void impactItems() {
        UUID client = UUID.randomUUID();
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "OPEN", TODAY.plusDays(7)).severity()).isEqualTo("HIGH");
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "OPEN", TODAY.plusDays(20)).severity()).isEqualTo("MEDIUM");
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "OPEN", TODAY.plusDays(90)).severity()).isEqualTo("LOW");
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "OPEN", null).severity()).isEqualTo("LOW");
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "OPEN", TODAY.minusDays(3)).severity()).isEqualTo("HIGH");
        assertThat(RegulatoryRules.impactItem(TODAY, client, "Acme", "T", "NOTICE_GIVEN", null).detail().contains("client told")).isTrue();
    }

    @Test @DisplayName("a change waiting a week or more moves up to medium, and only admins see it")
    void reviewItems() {
        Instant tenDaysAgo = TODAY.minusDays(10).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        var old = RegulatoryRules.reviewItem(TODAY, "T", "DETECTED", tenDaysAgo);
        assertThat(old.severity()).isEqualTo("MEDIUM");
        assertThat(old.adminOnly()).isTrue();
        assertThat(RegulatoryRules.reviewItem(TODAY, "T", "IN_REVIEW", TODAY.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)).severity()).isEqualTo("LOW");
    }
}
