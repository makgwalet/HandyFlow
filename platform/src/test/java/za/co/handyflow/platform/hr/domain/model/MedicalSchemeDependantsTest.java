package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicalSchemeDependantsTest {

    @Test
    void aBareTotalIsKeptAsOtherBecauseTheSplitIsNotKnown() {
        var d = MedicalSchemeRules.dependantsOf(3, null, null, null);
        assertThat(d.total()).isEqualTo(3);
        assertThat(d.other()).isEqualTo(3);
        assertThat(d.spouses()).isZero();
    }

    @Test
    void theSplitAddsUpToTheTotalTheCreditCounts() {
        var d = MedicalSchemeRules.dependantsOf(null, 1, 2, 0);
        assertThat(d.total()).isEqualTo(3);
        assertThat(MedicalSchemeRules.dependantsOf(3, 1, 2, null).total()).isEqualTo(3);
    }

    @Test
    void aTotalThatDisagreesWithTheSplitIsRefused() {
        assertThatThrownBy(() -> MedicalSchemeRules.dependantsOf(5, 1, 2, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("do not add up");
    }

    @Test
    void oneSpouseAtMostAndTwentyInAll() {
        assertThatThrownBy(() -> MedicalSchemeRules.dependantsOf(null, 2, 0, 0)).hasMessageContaining("one spouse");
        assertThatThrownBy(() -> MedicalSchemeRules.dependantsOf(null, 1, 15, 10)).hasMessageContaining("At most 20");
        assertThatThrownBy(() -> MedicalSchemeRules.dependantsOf(null, null, null, null)).hasMessageContaining("how many");
        assertThatThrownBy(() -> MedicalSchemeRules.dependantsOf(21, null, null, null)).hasMessageContaining("0 to 20");
    }

    @Test
    void contributionsAreZeroOrMoreWithTwoDecimals() {
        assertThat(MedicalSchemeRules.contribution(null, "employee")).isNull();
        assertThat(MedicalSchemeRules.contribution(new BigDecimal("1850.50"), "employee")).isEqualByComparingTo("1850.50");
        assertThatThrownBy(() -> MedicalSchemeRules.contribution(new BigDecimal("-1"), "employer")).hasMessageContaining("employer").hasMessageContaining("negative");
        assertThatThrownBy(() -> MedicalSchemeRules.contribution(new BigDecimal("10.123"), "employee")).hasMessageContaining("two decimals");
    }
}
