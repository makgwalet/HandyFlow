package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.accounting.application.internal.InvoiceLedgerRepairRules.*;

class InvoiceLedgerRepairRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate LOCKED = LocalDate.of(2026, 9, 30);

    @Test
    void anOpenOriginalPeriodKeepsTheOriginalDate() {
        LocalDate issued = LocalDate.of(2026, 10, 12);
        assertThat(category(issued, TODAY, LOCKED)).isEqualTo(ORIGINAL_DATE);
        assertThat(entryDate(ORIGINAL_DATE, issued, TODAY)).isEqualTo(issued);
        assertThat(entryType(ORIGINAL_DATE)).isEqualTo("INVOICE");
    }

    @Test
    void whenNothingIsLockedEveryInvoiceKeepsItsOriginalDate() {
        assertThat(category(LocalDate.of(2026, 2, 1), TODAY, null)).isEqualTo(ORIGINAL_DATE);
    }

    @Test
    void aLockedOriginalPeriodIsPostedNowAsAPriorPeriodCorrection() {
        LocalDate issued = LocalDate.of(2026, 6, 11);
        assertThat(category(issued, TODAY, LOCKED)).isEqualTo(PRIOR_PERIOD_CORRECTION);
        assertThat(entryDate(PRIOR_PERIOD_CORRECTION, issued, TODAY)).isEqualTo(TODAY);
        assertThat(entryType(PRIOR_PERIOD_CORRECTION)).isEqualTo("ADJUSTMENT");
    }

    @Test
    void theLockDateItselfIsClosed() {
        assertThat(category(LOCKED, TODAY, LOCKED)).isEqualTo(PRIOR_PERIOD_CORRECTION);
        assertThat(category(LOCKED.plusDays(1), TODAY, LOCKED)).isEqualTo(ORIGINAL_DATE);
    }

    @Test
    void anInvoiceWithNoRecordedDateIsPostedNowAsACorrection() {
        assertThat(category(null, TODAY, LOCKED)).isEqualTo(PRIOR_PERIOD_CORRECTION);
        assertThat(entryDate(PRIOR_PERIOD_CORRECTION, null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void whenTodayIsLockedTooNothingCanBePosted() {
        LocalDate lockedThroughToday = TODAY;
        assertThat(category(LocalDate.of(2026, 6, 11), TODAY, lockedThroughToday)).isEqualTo(UNPOSTABLE);
        assertThat(entryDate(UNPOSTABLE, LocalDate.of(2026, 6, 11), TODAY)).isNull();
    }

    @Test
    void theRepairNeedsAWrittenReason() {
        assertThat(reasonProblem(null)).contains("reason");
        assertThat(reasonProblem("short")).contains("reason");
        assertThat(reasonProblem("Recurring invoices were never posted before 0647")).isNull();
        assertThat(reasonProblem("x".repeat(501))).contains("500");
    }

    @Test
    void theApprovalHashChangesWhenAnInvoiceOrAnAmountChanges() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String base = previewHash(List.of(a, b), List.of(new BigDecimal("100.00"), new BigDecimal("50")));
        assertThat(previewHash(List.of(a, b), List.of(new BigDecimal("100"), new BigDecimal("50.00")))).isEqualTo(base);
        assertThat(previewHash(List.of(a), List.of(new BigDecimal("100")))).isNotEqualTo(base);
        assertThat(previewHash(List.of(a, b), List.of(new BigDecimal("100.01"), new BigDecimal("50")))).isNotEqualTo(base);
        assertThat(previewHash(List.of(b, a), List.of(new BigDecimal("50"), new BigDecimal("100")))).isNotEqualTo(base);
    }
}
