package za.co.handyflow.platform.crm.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pipeline stage rules on a lead: lost needs a reason, closed leads must be reopened, the reason is cleared on reopening. */
class CustomerStageRulesTest {

    private static final UUID USER = UUID.randomUUID();

    private Customer lead() {
        return Customer.create(TenantId.generate(), "Acme Mining", "a@acme.co", null, null, null, null, CustomerType.LEAD, USER);
    }

    @Test @DisplayName("moving to LOST needs a reason")
    void lostNeedsReason() {
        assertThatThrownBy(() -> lead().changeStage(LeadStage.LOST, USER, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("why");
    }

    @Test @DisplayName("reason OTHER needs a note, other reasons do not")
    void otherNeedsNote() {
        assertThatThrownBy(() -> lead().changeStage(LeadStage.LOST, USER, LostReason.OTHER, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("note");
        var c = lead();
        c.changeStage(LeadStage.LOST, USER, LostReason.PRICE, null);
        assertThat(c.getPipelineStage()).isEqualTo(LeadStage.LOST);
        assertThat(c.getLostReason()).isEqualTo(LostReason.PRICE);
    }

    @Test @DisplayName("a note over 500 characters is refused")
    void longNote() {
        assertThatThrownBy(() -> lead().changeStage(LeadStage.LOST, USER, LostReason.OTHER, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("reopening a lost lead clears the reason and note")
    void reopenClears() {
        var c = lead();
        c.changeStage(LeadStage.LOST, USER, LostReason.OTHER, "  went with their cousin  ");
        assertThat(c.getLostNote()).isEqualTo("went with their cousin");
        c.changeStage(LeadStage.CONTACTED, USER, null, null);
        assertThat(c.getLostReason()).isNull();
        assertThat(c.getLostNote()).isNull();
    }

    @Test @DisplayName("a won lead cannot go straight to lost, nor a lost lead to won")
    void closedMustReopen() {
        var won = lead();
        won.changeStage(LeadStage.WON, USER);
        assertThatThrownBy(() -> won.changeStage(LeadStage.LOST, USER, LostReason.PRICE, null)).isInstanceOf(IllegalStateException.class);
        var lost = lead();
        lost.changeStage(LeadStage.LOST, USER, LostReason.PRICE, null);
        assertThatThrownBy(() -> lost.changeStage(LeadStage.WON, USER)).isInstanceOf(IllegalStateException.class);
        lost.changeStage(LeadStage.NEW, USER);
        lost.changeStage(LeadStage.WON, USER);
        assertThat(lost.getPipelineStage()).isEqualTo(LeadStage.WON);
    }

    @Test @DisplayName("the timeline entry records the reason")
    void timelineRecordsReason() {
        var c = lead();
        c.changeStage(LeadStage.LOST, USER, LostReason.COMPETITOR, null);
        var last = c.getActivities().get(c.getActivities().size() - 1);
        assertThat(last.getActivityType()).isEqualTo(ActivityType.STAGE_CHANGED);
        assertThat(last.getPayload()).containsEntry("to", "LOST").containsEntry("reason", "COMPETITOR");
    }

    @Test @DisplayName("only a won lead converts, once, keeping the win on record")
    void conversion() {
        var c = lead();
        assertThatThrownBy(() -> c.convertToCustomer(USER)).isInstanceOf(IllegalStateException.class).hasMessageContaining("won");
        c.changeStage(LeadStage.WON, USER);
        c.convertToCustomer(USER);
        assertThat(c.getCustomerType()).isEqualTo(CustomerType.CUSTOMER);
        assertThat(c.getPipelineStage()).isEqualTo(LeadStage.WON);
        assertThat(c.getActivities().get(c.getActivities().size() - 1).getActivityType()).isEqualTo(ActivityType.LEAD_CONVERTED);
        assertThatThrownBy(() -> c.convertToCustomer(USER)).isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
        assertThatThrownBy(() -> c.changeStage(LeadStage.NEW, USER)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a converted customer has no pipeline stage")
    void customerHasNoStage() {
        var c = Customer.create(TenantId.generate(), "Bravo", null, null, null, null, null, CustomerType.CUSTOMER, USER);
        assertThatThrownBy(() -> c.changeStage(LeadStage.WON, USER)).isInstanceOf(IllegalStateException.class);
    }
}
