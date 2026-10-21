package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrgRulesTest {

    private final UUID ceo = UUID.randomUUID(), head = UUID.randomUUID(), lead = UUID.randomUUID(), clerk = UUID.randomUUID(), outsider = UUID.randomUUID();
    private final Map<UUID, UUID> managerOf = new HashMap<>();

    OrgRulesTest() {
        managerOf.put(ceo, null);
        managerOf.put(head, ceo);
        managerOf.put(lead, head);
        managerOf.put(clerk, lead);
        managerOf.put(outsider, null);
    }

    @Test
    void anEmployeeCanHaveNoManager() {
        assertThat(OrgRules.managerProblem(clerk, null, managerOf)).isNull();
    }

    @Test
    void aSensibleManagerIsAccepted() {
        assertThat(OrgRules.managerProblem(clerk, head, managerOf)).isNull();
        assertThat(OrgRules.managerProblem(outsider, ceo, managerOf)).isNull();
    }

    @Test
    void nobodyManagesThemselves() {
        assertThat(OrgRules.managerProblem(clerk, clerk, managerOf)).contains("own manager");
    }

    @Test
    void aLoopIsRefused() {
        assertThat(OrgRules.managerProblem(ceo, clerk, managerOf)).contains("loop");
        assertThat(OrgRules.managerProblem(head, lead, managerOf)).contains("loop");
    }

    @Test
    void aManagerMustBeAnActiveEmployee() {
        assertThat(OrgRules.managerProblem(clerk, UUID.randomUUID(), managerOf)).contains("active employee");
    }

    @Test
    void aBrokenChainDoesNotHangTheCheck() {
        Map<UUID, UUID> cyclic = new HashMap<>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        cyclic.put(a, b); cyclic.put(b, a); cyclic.put(c, null);
        assertThat(OrgRules.managerProblem(c, a, cyclic)).isNull();   // terminates, and c is not in the loop
    }

    @Test
    void aTeamIsTheManagerAndEveryoneBelow() {
        assertThat(OrgRules.team(head, managerOf)).isEqualTo(Set.of(head, lead, clerk));
        assertThat(OrgRules.team(clerk, managerOf)).isEqualTo(Set.of(clerk));
        assertThat(OrgRules.team(ceo, managerOf)).isEqualTo(Set.of(ceo, head, lead, clerk));
    }
}
