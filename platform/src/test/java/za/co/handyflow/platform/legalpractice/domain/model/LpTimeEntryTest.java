package za.co.handyflow.platform.legalpractice.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entity-behaviour coverage for {@link LpTimeEntry} — a direct port of
 * {@code accountant.TimeEntry}'s own lifecycle. Covers the HALF_UP
 * {@code lineTotal()} calculation, {@code isEditable()}, {@code markBilled()}
 * and {@code writeOff()} state transitions (including illegal ones), and the
 * billable/non-billable status split at creation.
 */
class LpTimeEntryTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final UUID matterId = UUID.randomUUID();
    private final UUID attorneyId = UUID.randomUUID();

    private LpTimeEntry billableEntry(BigDecimal hours, BigDecimal hourlyRate) {
        return LpTimeEntry.create(tenantId, matterId, attorneyId, LocalDate.of(2026, 8, 1),
                hours, hourlyRate, "Drafting heads of argument", true);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("billable=true sets status UNBILLED")
        void billableSetsStatusUnbilled() {
            LpTimeEntry entry = billableEntry(new BigDecimal("2.5"), new BigDecimal("1500.00"));
            assertThat(entry.getStatus()).isEqualTo("UNBILLED");
            assertThat(entry.isBillable()).isTrue();
            assertThat(entry.isEditable()).isTrue();
        }

        @Test
        @DisplayName("billable=false sets status NON_BILLABLE")
        void nonBillableSetsStatusNonBillable() {
            LpTimeEntry entry = LpTimeEntry.create(tenantId, matterId, attorneyId, LocalDate.of(2026, 8, 1),
                    new BigDecimal("1.0"), new BigDecimal("1500.00"), "Internal admin call", false);
            assertThat(entry.getStatus()).isEqualTo("NON_BILLABLE");
            assertThat(entry.isBillable()).isFalse();
            assertThat(entry.isEditable()).isFalse();
        }

        @Test
        @DisplayName("null entryDate defaults to today")
        void nullEntryDateDefaultsToToday() {
            LpTimeEntry entry = LpTimeEntry.create(tenantId, matterId, attorneyId, null,
                    new BigDecimal("1.0"), new BigDecimal("1500.00"), "Call", true);
            assertThat(entry.getEntryDate()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    @DisplayName("lineTotal() — hours * hourlyRate, HALF_UP to 2dp")
    class LineTotal {

        @Test
        @DisplayName("an exact multiplication needs no rounding")
        void exactMultiplication() {
            LpTimeEntry entry = billableEntry(new BigDecimal("2.00"), new BigDecimal("1500.00"));
            assertThat(entry.lineTotal()).isEqualByComparingTo("3000.00");
        }

        @Test
        @DisplayName("a result landing exactly on .xx5 rounds HALF_UP")
        void halfUpRounding() {
            // 1.25 * 100.02 = 125.025 -> rounds to 125.03 under HALF_UP
            LpTimeEntry entry = billableEntry(new BigDecimal("1.25"), new BigDecimal("100.02"));
            assertThat(entry.lineTotal()).isEqualByComparingTo("125.03");
        }

        @Test
        @DisplayName("a fractional-hour entry rounds down when below the midpoint")
        void roundsDownBelowMidpoint() {
            // 1.333 * 100.00 = 133.300 -> rounds to 133.30
            LpTimeEntry entry = billableEntry(new BigDecimal("1.333"), new BigDecimal("100.00"));
            assertThat(entry.lineTotal()).isEqualByComparingTo("133.30");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("succeeds while UNBILLED")
        void succeedsWhileUnbilled() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.update(LocalDate.of(2026, 8, 2), new BigDecimal("3.0"), new BigDecimal("1600.00"), "Revised description");

            assertThat(entry.getEntryDate()).isEqualTo(LocalDate.of(2026, 8, 2));
            assertThat(entry.getHours()).isEqualByComparingTo("3.0");
            assertThat(entry.getHourlyRate()).isEqualByComparingTo("1600.00");
            assertThat(entry.getDescription()).isEqualTo("Revised description");
        }

        @Test
        @DisplayName("throws IllegalStateException once BILLED")
        void throwsOnceBilled() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.markBilled(UUID.randomUUID());

            assertThatThrownBy(() -> entry.update(LocalDate.now(), new BigDecimal("2.0"), new BigDecimal("1500.00"), "x"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not editable");
        }

        @Test
        @DisplayName("throws IllegalStateException on a NON_BILLABLE entry (never editable)")
        void throwsOnNonBillable() {
            LpTimeEntry entry = LpTimeEntry.create(tenantId, matterId, attorneyId, LocalDate.now(),
                    new BigDecimal("1.0"), new BigDecimal("1500.00"), "Admin", false);

            assertThatThrownBy(() -> entry.update(LocalDate.now(), new BigDecimal("2.0"), new BigDecimal("1500.00"), "x"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("markBilled()")
    class MarkBilled {

        @Test
        @DisplayName("UNBILLED -> BILLED succeeds and stamps invoiceId")
        void unbilledToBilledSucceeds() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            UUID invoiceId = UUID.randomUUID();

            entry.markBilled(invoiceId);

            assertThat(entry.getStatus()).isEqualTo("BILLED");
            assertThat(entry.getInvoiceId()).isEqualTo(invoiceId);
            assertThat(entry.isEditable()).isFalse();
        }

        @Test
        @DisplayName("BILLED -> markBilled() again throws IllegalStateException")
        void billedToBilledAgainThrows() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.markBilled(UUID.randomUUID());

            assertThatThrownBy(() -> entry.markBilled(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only an UNBILLED time entry can be billed");
        }

        @Test
        @DisplayName("NON_BILLABLE -> markBilled() throws IllegalStateException")
        void nonBillableToBilledThrows() {
            LpTimeEntry entry = LpTimeEntry.create(tenantId, matterId, attorneyId, LocalDate.now(),
                    new BigDecimal("1.0"), new BigDecimal("1500.00"), "Admin", false);

            assertThatThrownBy(() -> entry.markBilled(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("WRITTEN_OFF -> markBilled() throws IllegalStateException")
        void writtenOffToBilledThrows() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.writeOff();

            assertThatThrownBy(() -> entry.markBilled(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("writeOff()")
    class WriteOff {

        @Test
        @DisplayName("UNBILLED -> WRITTEN_OFF succeeds")
        void unbilledToWrittenOffSucceeds() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.writeOff();
            assertThat(entry.getStatus()).isEqualTo("WRITTEN_OFF");
            assertThat(entry.isEditable()).isFalse();
        }

        @Test
        @DisplayName("BILLED -> writeOff() throws IllegalStateException")
        void billedToWriteOffThrows() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.markBilled(UUID.randomUUID());

            assertThatThrownBy(entry::writeOff)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only an UNBILLED time entry can be written off");
        }

        @Test
        @DisplayName("WRITTEN_OFF -> writeOff() again throws IllegalStateException")
        void writtenOffToWriteOffAgainThrows() {
            LpTimeEntry entry = billableEntry(new BigDecimal("1.0"), new BigDecimal("1500.00"));
            entry.writeOff();

            assertThatThrownBy(entry::writeOff).isInstanceOf(IllegalStateException.class);
        }
    }
}
