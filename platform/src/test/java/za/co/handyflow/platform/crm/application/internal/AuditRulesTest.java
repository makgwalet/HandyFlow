package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AuditRulesTest {
    @Test
    void cleanTrimsBlanksAndCuts() {
        assertThat(AuditRules.clean(null, 10)).isNull();
        assertThat(AuditRules.clean("   ", 10)).isNull();
        assertThat(AuditRules.clean("  Acme  ", 10)).isEqualTo("Acme");
        assertThat(AuditRules.clean("x".repeat(10), 10)).hasSize(10).doesNotContain("…");
        assertThat(AuditRules.clean("x".repeat(11), 10)).hasSize(10).endsWith("…");
    }

    @Test
    void aDateRangeMustRunForwards() {
        AuditRules.checkRange(null, null);
        AuditRules.checkRange(LocalDate.of(2026, 10, 1), null);
        AuditRules.checkRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        assertThatThrownBy(() -> AuditRules.checkRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The start date cannot be after the end date");
    }

    @Test
    void pageSizeAndNumberAreKeptInBounds() {
        assertThat(AuditRules.pageSize(null)).isEqualTo(50);
        assertThat(AuditRules.pageSize(0)).isEqualTo(50);
        assertThat(AuditRules.pageSize(20)).isEqualTo(20);
        assertThat(AuditRules.pageSize(5000)).isEqualTo(100);
        assertThat(AuditRules.pageNumber(null)).isZero();
        assertThat(AuditRules.pageNumber(-3)).isZero();
        assertThat(AuditRules.pageNumber(4)).isEqualTo(4);
    }
}
