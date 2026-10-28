package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.notifications.application.Recipient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterApproversRulesTest {

    private final UUID maker = UUID.randomUUID(), boss = UUID.randomUUID();

    private Recipient user(UUID id, String name) { return Recipient.user(id, name, name + "@x.co.za", null); }

    @Test
    void theSubmitterIsNeverAnApprover() {
        List<Recipient> out = RecruiterApprovalRules.approversFor(List.of(user(maker, "Thandi"), user(boss, "Sam")), Recipient::userId, maker);
        assertThat(out.size()).isEqualTo(1);
        assertThat(out.get(0).userId()).isEqualTo(boss);
    }

    @Test
    void whenOnlyTheSubmitterHoldsThePermissionNobodyIsLeft() {
        assertThat(RecruiterApprovalRules.approversFor(List.of(user(maker, "Thandi")), Recipient::userId, maker).isEmpty()).isTrue();
        assertThat(RecruiterApprovalRules.approversFor(List.<Recipient>of(), Recipient::userId, maker).isEmpty()).isTrue();
    }

    @Test
    void sendingIsRefusedWhenNobodyElseCanAnswer() {
        assertThat(RecruiterApprovalRules.noApproverProblem(true)).isNull();
        assertThat(RecruiterApprovalRules.noApproverProblem(false)).contains("Nobody else can approve");
    }
}
