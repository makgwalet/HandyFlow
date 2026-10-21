package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.OpportunityRules.Item;
import za.co.handyflow.platform.crm.domain.model.LostReason;
import za.co.handyflow.platform.crm.domain.model.OpportunityStage;

class OpportunityRulesTest {

    @Test
    void nameIsRequiredTrimmedAndLimited() {
        assertThatThrownBy(() -> OpportunityRules.cleanName("  ")).isInstanceOf(IllegalArgumentException.class).hasMessage("Give the opportunity a name");
        assertThat(OpportunityRules.cleanName(" Rollout ")).isEqualTo("Rollout");
        assertThatThrownBy(() -> OpportunityRules.cleanName("x".repeat(256))).hasMessage("The name can be at most 255 characters");
    }

    @Test
    void amountCannotBeNegativeOrHuge() {
        assertThatThrownBy(() -> OpportunityRules.cleanAmount(new BigDecimal("-1"))).hasMessage("The amount cannot be negative");
        assertThatThrownBy(() -> OpportunityRules.cleanAmount(new BigDecimal("1e14"))).hasMessage("That amount is too large");
        assertThat(OpportunityRules.cleanAmount(null)).isEqualByComparingTo("0");
        assertThat(OpportunityRules.cleanAmount(new BigDecimal("10.005")).toString()).isEqualTo("10.01");
    }

    @Test
    void closedOpportunitiesMustBeReopenedBeforeTheOtherOutcome() {
        assertThatThrownBy(() -> OpportunityRules.checkMove(OpportunityStage.WON, OpportunityStage.LOST, LostReason.PRICE, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Reopen the opportunity before closing it as lost");
        assertThatThrownBy(() -> OpportunityRules.checkMove(OpportunityStage.LOST, OpportunityStage.WON, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Reopen the opportunity before closing it as won");
        OpportunityRules.checkMove(OpportunityStage.LOST, OpportunityStage.PROSPECTING, null, null);
        OpportunityRules.checkMove(OpportunityStage.WON, OpportunityStage.NEGOTIATION, null, null);
    }

    @Test
    void lostNeedsAReasonAndOtherNeedsANote() {
        assertThatThrownBy(() -> OpportunityRules.checkMove(OpportunityStage.PROPOSAL, OpportunityStage.LOST, null, null))
                .hasMessage("Choose why the opportunity was lost");
        assertThatThrownBy(() -> OpportunityRules.checkMove(OpportunityStage.PROPOSAL, OpportunityStage.LOST, LostReason.OTHER, " "))
                .hasMessage("Add a note saying why the opportunity was lost");
        OpportunityRules.checkMove(OpportunityStage.PROPOSAL, OpportunityStage.LOST, LostReason.OTHER, "Went in-house");
        assertThatThrownBy(() -> OpportunityRules.checkMove(OpportunityStage.QUALIFIED, OpportunityStage.QUALIFIED, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("It is already qualified");
    }

    @Test
    void onlyOpenOpportunitiesAreWeighted() {
        assertThat(OpportunityRules.weighted(OpportunityStage.PROPOSAL, new BigDecimal("1000")).toString()).isEqualTo("500.00");
        assertThat(OpportunityRules.weighted(OpportunityStage.WON, new BigDecimal("1000")).signum()).isZero();
        assertThat(OpportunityRules.weighted(OpportunityStage.LOST, new BigDecimal("1000")).signum()).isZero();
    }

    @Test
    void summaryAddsUpTheBoard() {
        var s = OpportunityRules.summarise(List.of(
                new Item(OpportunityStage.PROSPECTING, new BigDecimal("1000"), LocalDate.of(2026, 10, 1)),
                new Item(OpportunityStage.NEGOTIATION, new BigDecimal("2000"), LocalDate.of(2026, 11, 1)),
                new Item(OpportunityStage.WON, new BigDecimal("500"), LocalDate.of(2026, 9, 1)),
                new Item(OpportunityStage.LOST, new BigDecimal("700"), null)), LocalDate.of(2026, 10, 21));
        assertThat(s.openCount()).isEqualTo(2);
        assertThat(s.openValue().toString()).isEqualTo("3000.00");
        assertThat(s.weightedValue().toString()).isEqualTo("1600.00");
        assertThat(s.overdueCount()).isEqualTo(1);
        assertThat(s.wonCount()).isEqualTo(1);
        assertThat(s.wonValue().toString()).isEqualTo("500.00");
        assertThat(s.lostCount()).isEqualTo(1);
        assertThat(s.byStage()).hasSize(6);
        assertThat(OpportunityRules.summarise(List.of(), LocalDate.of(2026, 10, 21)).openCount()).isZero();
    }
}
