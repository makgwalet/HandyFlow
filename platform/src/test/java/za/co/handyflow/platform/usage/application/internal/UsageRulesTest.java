package za.co.handyflow.platform.usage.application.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageRulesTest {

    @Test
    void warnsAtEightyAndNinetyPercentAndAtTheLimit() {
        assertThat(UsageRules.level(39, 50)).isEqualTo(UsageRules.NONE);
        assertThat(UsageRules.level(40, 50)).isEqualTo(UsageRules.NEAR);
        assertThat(UsageRules.level(44, 50)).isEqualTo(UsageRules.NEAR);
        assertThat(UsageRules.level(45, 50)).isEqualTo(UsageRules.CLOSE);
        assertThat(UsageRules.level(49, 50)).isEqualTo(UsageRules.CLOSE);
        assertThat(UsageRules.level(50, 50)).isEqualTo(UsageRules.AT_LIMIT);
        assertThat(UsageRules.level(70, 50)).isEqualTo(UsageRules.AT_LIMIT);
    }

    @Test
    void noLimitMeansNothingToWarnAbout() {
        assertThat(UsageRules.level(10_000, null)).isEqualTo(UsageRules.NO_LIMIT);
        assertThat(UsageRules.percent(10_000, null)).isNull();
    }

    @Test
    void percentIsWholeAndMayPassOneHundred() {
        assertThat(UsageRules.percent(40, 50)).isEqualTo(80);
        assertThat(UsageRules.percent(75, 50)).isEqualTo(150);
    }
}
