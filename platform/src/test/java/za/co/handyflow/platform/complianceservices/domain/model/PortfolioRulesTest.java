package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRulesTest {

    private static PortfolioRules.Facts client(String name) { return new PortfolioRules.Facts(UUID.randomUUID(), name); }

    private static boolean has(List<PortfolioRules.Signal> list, String code, String severity) {
        return list.stream().anyMatch(s -> s.code().equals(code) && s.severity().equals(severity));
    }

    @Test @DisplayName("a client with nothing recorded against it has no signs, and is not called compliant")
    void quiet() {
        assertThat(PortfolioRules.signals(client("Quiet")).isEmpty()).isTrue();
        assertThat(PortfolioRules.intervention(List.of(client("Quiet"))).isEmpty()).isTrue();
    }

    @Test @DisplayName("one overdue deadline is a medium sign, three or more is high")
    void overdue() {
        PortfolioRules.Facts f = client("A");
        f.overdueDeadlines = 1; f.oldestOverdueDays = 4;
        assertThat(has(PortfolioRules.signals(f), "OVERDUE_DEADLINES", "MEDIUM")).isTrue();
        assertThat(PortfolioRules.signals(f).get(0).detail()).isEqualTo("1 deadline is overdue, the oldest by 4 days");
        f.overdueDeadlines = 3;
        assertThat(has(PortfolioRules.signals(f), "OVERDUE_DEADLINES", "HIGH")).isTrue();
    }

    @Test @DisplayName("expired documents are high, expiring ones medium")
    void documents() {
        PortfolioRules.Facts f = client("A");
        f.expiredDocuments = 1; f.expiringDocuments = 2;
        List<PortfolioRules.Signal> s = PortfolioRules.signals(f);
        assertThat(has(s, "EXPIRED_DOCUMENTS", "HIGH")).isTrue();
        assertThat(has(s, "EXPIRING_DOCUMENTS", "MEDIUM")).isTrue();
    }

    @Test @DisplayName("documents rejected three times in 90 days count as repeated; two do not")
    void rejections() {
        PortfolioRules.Facts f = client("A");
        f.rejectedRecently = 2;
        assertThat(PortfolioRules.signals(f).isEmpty()).isTrue();
        f.rejectedRecently = 3;
        assertThat(has(PortfolioRules.signals(f), "REPEATED_REJECTIONS", "MEDIUM")).isTrue();
    }

    @Test @DisplayName("serious open cases are high; overdue and long-open cases are medium")
    void cases() {
        PortfolioRules.Facts f = client("A");
        f.seriousCases = 1; f.overdueCases = 2; f.stalledCases = 1;
        List<PortfolioRules.Signal> s = PortfolioRules.signals(f);
        assertThat(has(s, "SERIOUS_CASES", "HIGH")).isTrue();
        assertThat(has(s, "CASES_OVERDUE", "MEDIUM")).isTrue();
        assertThat(has(s, "CASES_STALLED", "MEDIUM")).isTrue();
    }

    @Test @DisplayName("a regulatory change past its date is high; otherwise an open one is low, and only one of the two shows")
    void regulatory() {
        PortfolioRules.Facts f = client("A");
        f.openImpacts = 2;
        assertThat(has(PortfolioRules.signals(f), "REGULATORY_OPEN", "LOW")).isTrue();
        f.overdueImpacts = 1;
        List<PortfolioRules.Signal> s = PortfolioRules.signals(f);
        assertThat(has(s, "REGULATORY_OVERDUE", "HIGH")).isTrue();
        assertThat(has(s, "REGULATORY_OPEN", "LOW")).isFalse();
    }

    @Test @DisplayName("agreements: past the end date is high, none current or ending within 30 days is medium, a client that never had one is not flagged")
    void agreements() {
        PortfolioRules.Facts f = client("A");
        f.agreement = "PAST_END"; f.agreementDays = 3;
        assertThat(has(PortfolioRules.signals(f), "AGREEMENT_PAST_END", "HIGH")).isTrue();
        f.agreement = "LAPSED";
        assertThat(has(PortfolioRules.signals(f), "AGREEMENT_LAPSED", "MEDIUM")).isTrue();
        f.agreement = "ENDING_SOON"; f.agreementDays = 12;
        assertThat(has(PortfolioRules.signals(f), "AGREEMENT_ENDING", "MEDIUM")).isTrue();
        f.agreement = "NONE";
        assertThat(PortfolioRules.signals(f).isEmpty()).isTrue();
        f.agreement = "CURRENT";
        assertThat(PortfolioRules.signals(f).isEmpty()).isTrue();
    }

    @Test @DisplayName("the agreement state is worked out from what is recorded and the dates")
    void agreementState() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        assertThat(PortfolioRules.agreementState(List.of(), null, today)[0]).isEqualTo("NONE");
        assertThat(PortfolioRules.agreementState(List.of("DRAFT"), null, today)[0]).isEqualTo("NONE");
        assertThat(PortfolioRules.agreementState(List.of("ENDED", "CANCELLED"), null, today)[0]).isEqualTo("LAPSED");
        assertThat(PortfolioRules.agreementState(List.of("ENDED", "DRAFT"), null, today)[0]).isEqualTo("NONE");
        assertThat(PortfolioRules.agreementState(List.of("ACTIVE"), null, today)[0]).isEqualTo("CURRENT");
        assertThat(PortfolioRules.agreementState(List.of("ACTIVE"), today.plusDays(90), today)[0]).isEqualTo("CURRENT");
        String[] soon = PortfolioRules.agreementState(List.of("ACTIVE"), today.plusDays(10), today);
        assertThat(soon[0]).isEqualTo("ENDING_SOON");
        assertThat(soon[1]).isEqualTo("10");
        String[] past = PortfolioRules.agreementState(List.of("ACTIVE"), today.minusDays(2), today);
        assertThat(past[0]).isEqualTo("PAST_END");
        assertThat(past[1]).isEqualTo("2");
    }

    @Test @DisplayName("a high rating alone is a low sign")
    void rating() {
        PortfolioRules.Facts f = client("A");
        f.riskRating = "HIGH";
        List<PortfolioRules.Signal> s = PortfolioRules.signals(f);
        assertThat(s.size()).isEqualTo(1);
        assertThat(s.get(0).severity()).isEqualTo("LOW");
    }

    @Test @DisplayName("signs are listed most serious first")
    void order() {
        PortfolioRules.Facts f = client("A");
        f.riskRating = "HIGH"; f.expiringDocuments = 1; f.expiredDocuments = 1;
        List<PortfolioRules.Signal> s = PortfolioRules.signals(f);
        assertThat(s.get(0).severity()).isEqualTo("HIGH");
        assertThat(s.get(s.size() - 1).severity()).isEqualTo("LOW");
    }

    @Test @DisplayName("clients are ranked by high signs, then medium, then low, then name; clients with none are left out")
    void ranking() {
        PortfolioRules.Facts one = client("Zeta");  one.expiredDocuments = 1;
        PortfolioRules.Facts two = client("Alpha"); two.expiredDocuments = 1; two.seriousCases = 1;
        PortfolioRules.Facts med = client("Mid");   med.overdueDeadlines = 1;
        PortfolioRules.Facts tie = client("Beta");  tie.expiredDocuments = 1;
        PortfolioRules.Facts none = client("None");
        List<PortfolioRules.Entry> out = PortfolioRules.intervention(List.of(med, one, none, two, tie));
        assertThat(out.stream().map(PortfolioRules.Entry::clientName).toList()).isEqualTo(List.of("Alpha", "Beta", "Zeta", "Mid"));
        assertThat(out.get(0).high()).isEqualTo(2);
    }
}
