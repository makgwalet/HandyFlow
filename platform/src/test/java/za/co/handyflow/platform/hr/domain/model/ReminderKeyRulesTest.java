package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderKeyRulesTest {

    @Test
    void theSameMessageToTheSamePersonGivesTheSameKey() {
        String a = ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:a@x.co.za", "Late", "Text");
        assertThat(a.length()).isEqualTo(64);
        assertThat(a).isEqualTo(ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:a@x.co.za", "Late", "Text"));
    }

    @Test
    void anyDifferenceMakesAnotherKey() {
        String base = ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:a@x.co.za", "Late", "Text");
        assertThat(ReminderKeyRules.key("LEAVE_AT_RISK", "case-1", "e:a@x.co.za", "Late", "Text").equals(base)).isFalse();
        assertThat(ReminderKeyRules.key("ER_CASE_DUE", "case-2", "e:a@x.co.za", "Late", "Text").equals(base)).isFalse();
        assertThat(ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:b@x.co.za", "Late", "Text").equals(base)).isFalse();
        assertThat(ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:a@x.co.za", "Late!", "Text").equals(base)).isFalse();
        assertThat(ReminderKeyRules.key("ER_CASE_DUE", "case-1", "e:a@x.co.za", "Late", "Text 2").equals(base)).isFalse();
    }

    @Test
    void partsAreNotRunTogether() {
        assertThat(ReminderKeyRules.key("A", "BC", "p", "t", "m").equals(ReminderKeyRules.key("AB", "C", "p", "t", "m"))).isFalse();
    }

    @Test
    void aPersonIsToldApartByUserThenEmailThenName() {
        UUID u = UUID.randomUUID();
        assertThat(ReminderKeyRules.person(u, "A@X.co.za", "Ann")).isEqualTo("u:" + u);
        assertThat(ReminderKeyRules.person(null, " A@X.co.za ", "Ann")).isEqualTo("e:a@x.co.za");
        assertThat(ReminderKeyRules.person(null, null, "Ann")).isEqualTo("n:ann");
        assertThat(ReminderKeyRules.person(null, " ", null)).isNull();
    }
}
