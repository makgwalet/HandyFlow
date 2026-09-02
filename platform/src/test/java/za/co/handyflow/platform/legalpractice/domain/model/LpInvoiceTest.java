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
 * Entity-behaviour coverage for {@link LpInvoice} — flat-15% VAT
 * calculation at {@code create()}, the DRAFT -&gt; SENT -&gt;
 * PARTIALLY_PAID/PAID status machine driven by {@code applyPayment()},
 * {@code writeOff()}, and {@code getOutstandingBalance()}.
 */
class LpInvoiceTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final UUID clientId = UUID.randomUUID();
    private final UUID matterId = UUID.randomUUID();

    private LpInvoice draftInvoice(BigDecimal subtotal) {
        return LpInvoice.create(tenantId, clientId, matterId, "INV-0001", "Professional fees",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), subtotal, "Thank you");
    }

    @Nested
    @DisplayName("create() — VAT calculation")
    class Create {

        @Test
        @DisplayName("computes vatAmount as flat 15% of subtotal, HALF_UP to 2dp")
        void computesFlatFifteenPercentVat() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));

            assertThat(invoice.getSubtotal()).isEqualByComparingTo("1000.00");
            assertThat(invoice.getVatAmount()).isEqualByComparingTo("150.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1150.00");
        }

        @Test
        @DisplayName("rounds a fractional VAT amount HALF_UP")
        void roundsFractionalVatHalfUp() {
            // 33.33 * 0.15 = 4.9995 -> rounds to 5.00
            LpInvoice invoice = draftInvoice(new BigDecimal("33.33"));

            assertThat(invoice.getVatAmount()).isEqualByComparingTo("5.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("38.33");
        }

        @Test
        @DisplayName("starts DRAFT with zero amountPaid")
        void startsDraftWithZeroAmountPaid() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            assertThat(invoice.getStatus()).isEqualTo("DRAFT");
            assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
            assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("1150.00");
        }

        @Test
        @DisplayName("null issueDate defaults to today")
        void nullIssueDateDefaultsToToday() {
            LpInvoice invoice = LpInvoice.create(tenantId, clientId, matterId, "INV-0002", null,
                    null, null, new BigDecimal("100.00"), null);
            assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("matterId is nullable for a retainer-only invoice")
        void matterIdIsNullableForRetainerOnlyInvoice() {
            LpInvoice invoice = LpInvoice.create(tenantId, clientId, null, "INV-0003", "Retainer",
                    LocalDate.now(), null, new BigDecimal("500.00"), null);
            assertThat(invoice.getMatterId()).isNull();
        }
    }

    @Nested
    @DisplayName("markSent()")
    class MarkSent {

        @Test
        @DisplayName("DRAFT -> SENT succeeds")
        void draftToSentSucceeds() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            assertThat(invoice.getStatus()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("SENT -> markSent() again throws IllegalStateException")
        void sentToMarkSentAgainThrows() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            assertThatThrownBy(invoice::markSent)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only a DRAFT invoice can be sent");
        }
    }

    @Nested
    @DisplayName("applyPayment()")
    class ApplyPayment {

        @Test
        @DisplayName("a partial payment on a SENT invoice moves it to PARTIALLY_PAID")
        void partialPaymentMovesToPartiallyPaid() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00")); // total 1150.00
            invoice.markSent();

            invoice.applyPayment(new BigDecimal("500.00"));

            assertThat(invoice.getStatus()).isEqualTo("PARTIALLY_PAID");
            assertThat(invoice.getAmountPaid()).isEqualByComparingTo("500.00");
            assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("650.00");
        }

        @Test
        @DisplayName("a full payment on a SENT invoice moves it straight to PAID")
        void fullPaymentMovesStraightToPaid() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00")); // total 1150.00
            invoice.markSent();

            invoice.applyPayment(new BigDecimal("1150.00"));

            assertThat(invoice.getStatus()).isEqualTo("PAID");
            assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("an overpayment still results in PAID (>= totalAmount)")
        void overpaymentStillResultsInPaid() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00")); // total 1150.00
            invoice.markSent();

            invoice.applyPayment(new BigDecimal("1200.00"));

            assertThat(invoice.getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("two partial payments accumulate and eventually reach PAID")
        void twoPartialPaymentsAccumulate() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00")); // total 1150.00
            invoice.markSent();

            invoice.applyPayment(new BigDecimal("600.00"));
            assertThat(invoice.getStatus()).isEqualTo("PARTIALLY_PAID");

            invoice.applyPayment(new BigDecimal("550.00"));
            assertThat(invoice.getStatus()).isEqualTo("PAID");
            assertThat(invoice.getAmountPaid()).isEqualByComparingTo("1150.00");
        }

        @Test
        @DisplayName("a payment on a still-DRAFT invoice throws IllegalStateException")
        void paymentOnDraftThrows() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            assertThatThrownBy(() -> invoice.applyPayment(new BigDecimal("100.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot apply a payment to an invoice in status DRAFT");
        }

        @Test
        @DisplayName("a payment on a WRITTEN_OFF invoice throws IllegalStateException")
        void paymentOnWrittenOffThrows() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            invoice.writeOff();

            assertThatThrownBy(() -> invoice.applyPayment(new BigDecimal("100.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WRITTEN_OFF");
        }

        @Test
        @DisplayName("a null payment amount throws IllegalArgumentException")
        void nullPaymentAmountThrows() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            assertThatThrownBy(() -> invoice.applyPayment(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("a zero or negative payment amount throws IllegalArgumentException")
        void zeroOrNegativePaymentAmountThrows() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            assertThatThrownBy(() -> invoice.applyPayment(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> invoice.applyPayment(new BigDecimal("-10.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("writeOff()")
    class WriteOff {

        @Test
        @DisplayName("a SENT invoice can be written off")
        void sentInvoiceCanBeWrittenOff() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            invoice.writeOff();
            assertThat(invoice.getStatus()).isEqualTo("WRITTEN_OFF");
        }

        @Test
        @DisplayName("a PARTIALLY_PAID invoice can be written off")
        void partiallyPaidInvoiceCanBeWrittenOff() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            invoice.applyPayment(new BigDecimal("200.00"));
            invoice.writeOff();
            assertThat(invoice.getStatus()).isEqualTo("WRITTEN_OFF");
        }

        @Test
        @DisplayName("a fully PAID invoice cannot be written off")
        void fullyPaidInvoiceCannotBeWrittenOff() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.markSent();
            invoice.applyPayment(new BigDecimal("1150.00"));

            assertThatThrownBy(invoice::writeOff)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("A fully PAID invoice cannot be written off");
        }

        @Test
        @DisplayName("a still-DRAFT invoice can be written off (no explicit guard against DRAFT)")
        void draftInvoiceCanBeWrittenOff() {
            LpInvoice invoice = draftInvoice(new BigDecimal("1000.00"));
            invoice.writeOff();
            assertThat(invoice.getStatus()).isEqualTo("WRITTEN_OFF");
        }
    }

    @Test
    @DisplayName("getOutstandingBalance() reflects totalAmount minus amountPaid at every stage")
    void outstandingBalanceReflectsPaymentsAtEveryStage() {
        LpInvoice invoice = draftInvoice(new BigDecimal("2000.00")); // total 2300.00
        assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("2300.00");

        invoice.markSent();
        invoice.applyPayment(new BigDecimal("800.00"));
        assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("1500.00");

        invoice.applyPayment(new BigDecimal("1500.00"));
        assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("0.00");
    }
}
