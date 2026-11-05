package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.accounting.application.internal.InvoiceLedgerRepairRules.*;

class InvoiceLedgerRepairRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void aPolicyMustBeChosen() {
        assertThat(validPolicy(ORIGINAL)).isTrue();
        assertThat(validPolicy(TODAY_POLICY())).isTrue();
        assertThat(validPolicy(null)).isFalse();
        assertThat(validPolicy("whenever")).isFalse();
    }

    private static String TODAY_POLICY() { return InvoiceLedgerRepairRules.TODAY; }

    @Test
    void originalUsesTheIssueDateAndTodayUsesToday() {
        LocalDate issued = LocalDate.of(2026, 6, 11);
        assertThat(entryDate(ORIGINAL, issued, TODAY)).isEqualTo(issued);
        assertThat(entryDate(InvoiceLedgerRepairRules.TODAY, issued, TODAY)).isEqualTo(TODAY);
        assertThat(entryDate(ORIGINAL, null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void aDateOnOrBeforeTheLockDateCannotBePosted() {
        LocalDate locked = LocalDate.of(2026, 9, 30);
        assertThat(skipReason(LocalDate.of(2026, 9, 30), locked)).contains("locked through 2026-09-30");
        assertThat(skipReason(LocalDate.of(2026, 6, 11), locked)).isNotNull();
        assertThat(skipReason(LocalDate.of(2026, 10, 1), locked)).isNull();
        assertThat(skipReason(LocalDate.of(2026, 6, 11), null)).isNull();
    }
}
