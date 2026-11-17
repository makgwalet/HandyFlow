package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.DocView;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.ItemStatus;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.Overall;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules.Metrics;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType.*;

class SupplierComplianceAndScoreRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Set<SupplierDocType> REQUIRED = EnumSet.of(TAX_CLEARANCE, COID_GOOD_STANDING);

    private SupplierComplianceRules.Result eval(List<DocView> docs, boolean requireVerified) {
        return SupplierComplianceRules.evaluate(REQUIRED, docs, TODAY, 30, requireVerified);
    }

    @Test
    void noPolicy_isNotRequired() {
        assertThat(SupplierComplianceRules.evaluate(Set.of(), List.of(), TODAY, 30, false).overall()).isEqualTo(Overall.NOT_REQUIRED);
    }

    @Test
    void missingRequiredDocument_isNonCompliant() {
        var r = eval(List.of(new DocView(TAX_CLEARANCE, TODAY.plusDays(200), true)), false);
        assertThat(r.overall()).isEqualTo(Overall.NON_COMPLIANT);
        assertThat(r.problems()).extracting(SupplierComplianceRules.Item::type).containsExactly(COID_GOOD_STANDING);
    }

    @Test
    void validOnExpiryDate_expiredTheDayAfter() {
        var onDay = eval(List.of(new DocView(TAX_CLEARANCE, TODAY, true), new DocView(COID_GOOD_STANDING, TODAY.plusDays(90), true)), false);
        assertThat(onDay.overall()).isEqualTo(Overall.EXPIRING);                 // valid, inside the warning window
        var dayAfter = eval(List.of(new DocView(TAX_CLEARANCE, TODAY.minusDays(1), true), new DocView(COID_GOOD_STANDING, TODAY.plusDays(90), true)), false);
        assertThat(dayAfter.overall()).isEqualTo(Overall.NON_COMPLIANT);
        assertThat(dayAfter.items()).anyMatch(i -> i.type() == TAX_CLEARANCE && i.status() == ItemStatus.EXPIRED);
    }

    @Test
    void allValid_isCompliant_andNoExpiryNeverExpires() {
        var r = eval(List.of(new DocView(TAX_CLEARANCE, TODAY.plusDays(120), true), new DocView(COID_GOOD_STANDING, null, true)), false);
        assertThat(r.overall()).isEqualTo(Overall.COMPLIANT);
    }

    @Test
    void bestDocumentWins_whenSeveralActive() {
        var r = eval(List.of(new DocView(TAX_CLEARANCE, TODAY.minusDays(5), true), new DocView(TAX_CLEARANCE, TODAY.plusDays(300), true),
                new DocView(COID_GOOD_STANDING, TODAY.plusDays(300), true)), false);
        assertThat(r.overall()).isEqualTo(Overall.COMPLIANT);
    }

    @Test
    void unverified_blocksOnlyWhenVerificationRequired() {
        var docs = List.of(new DocView(TAX_CLEARANCE, TODAY.plusDays(200), false), new DocView(COID_GOOD_STANDING, TODAY.plusDays(200), true));
        assertThat(eval(docs, false).overall()).isEqualTo(Overall.COMPLIANT);
        assertThat(eval(docs, true).overall()).isEqualTo(Overall.NON_COMPLIANT);
    }

    @Test
    void score_weightsAndRatings() {
        var perfect = SupplierScoreRules.score(new Metrics(100.0, 100.0, 100.0, 100.0));
        assertThat(perfect.score()).isEqualTo(100);
        assertThat(perfect.rating()).isEqualTo("A");
        // 0.35*80 + 0.20*100 + 0.25*100 + 0.20*50 = 28+20+25+10 = 83
        var mixed = SupplierScoreRules.score(new Metrics(80.0, 100.0, 100.0, 50.0));
        assertThat(mixed.score()).isEqualTo(83);
        assertThat(mixed.rating()).isEqualTo("B");
        assertThat(SupplierScoreRules.score(new Metrics(40.0, 50.0, 60.0, 50.0)).rating()).isEqualTo("D");
    }

    @Test
    void score_missingComponentsAreRenormalised_andNoDataMeansNoScore() {
        // Only on-time (35) and quality (25) have data: (90*35 + 100*25) / 60 = 94.17 -> 94
        var s = SupplierScoreRules.score(new Metrics(90.0, null, 100.0, null));
        assertThat(s.score()).isEqualTo(94);
        var none = SupplierScoreRules.score(new Metrics(null, null, null, null));
        assertThat(none.score()).isNull();
        assertThat(none.rating()).isNull();
    }

    @Test
    void pct_handlesZeroDenominator() {
        assertThat(SupplierScoreRules.pct(0, 0)).isNull();
        assertThat(SupplierScoreRules.pct(3, 4)).isEqualTo(75.0);
    }
}
