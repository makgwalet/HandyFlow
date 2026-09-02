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
 * The most thorough test class in this module, matching the task brief's
 * explicit instruction: {@link LpTrustTransaction} is the highest
 * compliance-risk entity here — every one of the four types'
 * factory-enforced required/forbidden field rules is exercised in both its
 * success and failure shape, plus the positive-amount guard and
 * {@code increasesTrustBalance()}.
 * <p>
 * Uses real {@link TenantId#of(UUID)} instances (not a mocked
 * {@code TenantId}) per this task's explicit instruction.
 */
class LpTrustTransactionTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final UUID clientId = UUID.randomUUID();
    private final UUID matterId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID capturedBy = UUID.randomUUID();

    // ── Amount guard (applies to every type) ────────────────────────────────

    @Nested
    @DisplayName("amount must be positive, regardless of type")
    class AmountGuard {

        @Test
        @DisplayName("null amount throws IllegalArgumentException")
        void nullAmountThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    null, LocalDate.now(), null, null, "ref", capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("zero amount throws IllegalArgumentException")
        void zeroAmountThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    BigDecimal.ZERO, LocalDate.now(), null, null, "ref", capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("negative amount throws IllegalArgumentException")
        void negativeAmountThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    new BigDecimal("-100.00"), LocalDate.now(), null, null, "ref", capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }
    }

    // ── RECEIPT ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RECEIPT — forbids invoiceId and payee")
    class Receipt {

        @Test
        @DisplayName("succeeds with neither invoiceId nor payee, defaults transactionDate to today")
        void succeedsWithNeitherInvoiceIdNorPayee() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    new BigDecimal("5000.00"), null, null, null, "EFT ref 123", capturedBy, "Jane Attorney", "Deposit");

            assertThat(tx.getTransactionType()).isEqualTo("RECEIPT");
            assertThat(tx.getInvoiceId()).isNull();
            assertThat(tx.getPayee()).isNull();
            assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.now());
            assertThat(tx.getAmount()).isEqualByComparingTo("5000.00");
            assertThat(tx.getClientId()).isEqualTo(clientId);
            assertThat(tx.getMatterId()).isEqualTo(matterId);
        }

        @Test
        @DisplayName("succeeds with a null matterId — a client-level receipt not yet tied to a matter")
        void succeedsWithNullMatterId() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, null, "RECEIPT",
                    new BigDecimal("1000.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null);
            assertThat(tx.getMatterId()).isNull();
        }

        @Test
        @DisplayName("carrying an invoiceId throws IllegalArgumentException")
        void withInvoiceIdThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    new BigDecimal("5000.00"), LocalDate.now(), invoiceId, null, null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RECEIPT must not carry an invoiceId");
        }

        @Test
        @DisplayName("carrying a payee throws IllegalArgumentException")
        void withPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    new BigDecimal("5000.00"), LocalDate.now(), null, "Sheriff of the Court", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RECEIPT must not carry a payee");
        }

        @Test
        @DisplayName("increasesTrustBalance() is true")
        void increasesTrustBalanceIsTrue() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                    new BigDecimal("100.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null);
            assertThat(tx.increasesTrustBalance()).isTrue();
        }
    }

    // ── TRANSFER_TO_BUSINESS ─────────────────────────────────────────────

    @Nested
    @DisplayName("TRANSFER_TO_BUSINESS — requires invoiceId, forbids payee")
    class TransferToBusiness {

        @Test
        @DisplayName("succeeds with an invoiceId and no payee")
        void succeedsWithInvoiceIdNoPayee() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "TRANSFER_TO_BUSINESS",
                    new BigDecimal("2500.00"), LocalDate.now(), invoiceId, null, "Settle fee note", capturedBy, "Jane Attorney", null);

            assertThat(tx.getTransactionType()).isEqualTo("TRANSFER_TO_BUSINESS");
            assertThat(tx.getInvoiceId()).isEqualTo(invoiceId);
            assertThat(tx.getPayee()).isNull();
        }

        @Test
        @DisplayName("missing invoiceId (null) throws IllegalArgumentException")
        void missingInvoiceIdThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "TRANSFER_TO_BUSINESS",
                    new BigDecimal("2500.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TRANSFER_TO_BUSINESS requires an invoiceId");
        }

        @Test
        @DisplayName("carrying a payee throws IllegalArgumentException even with a valid invoiceId")
        void withPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "TRANSFER_TO_BUSINESS",
                    new BigDecimal("2500.00"), LocalDate.now(), invoiceId, "Some Payee", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TRANSFER_TO_BUSINESS must not carry a payee");
        }

        @Test
        @DisplayName("increasesTrustBalance() is false")
        void increasesTrustBalanceIsFalse() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "TRANSFER_TO_BUSINESS",
                    new BigDecimal("100.00"), LocalDate.now(), invoiceId, null, null, capturedBy, "Jane Attorney", null);
            assertThat(tx.increasesTrustBalance()).isFalse();
        }
    }

    // ── DISBURSEMENT_PAYMENT ─────────────────────────────────────────────

    @Nested
    @DisplayName("DISBURSEMENT_PAYMENT — requires payee, forbids invoiceId")
    class DisbursementPayment {

        @Test
        @DisplayName("succeeds with a payee and no invoiceId")
        void succeedsWithPayeeNoInvoiceId() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "DISBURSEMENT_PAYMENT",
                    new BigDecimal("850.00"), LocalDate.now(), null, "Sheriff of the Court", "Service of process", capturedBy, "Jane Attorney", null);

            assertThat(tx.getTransactionType()).isEqualTo("DISBURSEMENT_PAYMENT");
            assertThat(tx.getPayee()).isEqualTo("Sheriff of the Court");
            assertThat(tx.getInvoiceId()).isNull();
        }

        @Test
        @DisplayName("null payee throws IllegalArgumentException")
        void nullPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "DISBURSEMENT_PAYMENT",
                    new BigDecimal("850.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DISBURSEMENT_PAYMENT requires a payee");
        }

        @Test
        @DisplayName("blank payee throws IllegalArgumentException")
        void blankPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "DISBURSEMENT_PAYMENT",
                    new BigDecimal("850.00"), LocalDate.now(), null, "   ", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DISBURSEMENT_PAYMENT requires a payee");
        }

        @Test
        @DisplayName("carrying an invoiceId throws IllegalArgumentException even with a valid payee")
        void withInvoiceIdThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "DISBURSEMENT_PAYMENT",
                    new BigDecimal("850.00"), LocalDate.now(), invoiceId, "Sheriff of the Court", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DISBURSEMENT_PAYMENT must not carry an invoiceId");
        }

        @Test
        @DisplayName("increasesTrustBalance() is false")
        void increasesTrustBalanceIsFalse() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "DISBURSEMENT_PAYMENT",
                    new BigDecimal("100.00"), LocalDate.now(), null, "Counsel", null, capturedBy, "Jane Attorney", null);
            assertThat(tx.increasesTrustBalance()).isFalse();
        }
    }

    // ── REFUND ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("REFUND — requires payee, forbids invoiceId")
    class Refund {

        @Test
        @DisplayName("succeeds with a payee and no invoiceId")
        void succeedsWithPayeeNoInvoiceId() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "REFUND",
                    new BigDecimal("300.00"), LocalDate.now(), null, "Client themselves", "Unused balance", capturedBy, "Jane Attorney", null);

            assertThat(tx.getTransactionType()).isEqualTo("REFUND");
            assertThat(tx.getPayee()).isEqualTo("Client themselves");
            assertThat(tx.getInvoiceId()).isNull();
        }

        @Test
        @DisplayName("null payee throws IllegalArgumentException")
        void nullPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "REFUND",
                    new BigDecimal("300.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REFUND requires a payee");
        }

        @Test
        @DisplayName("blank payee throws IllegalArgumentException")
        void blankPayeeThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "REFUND",
                    new BigDecimal("300.00"), LocalDate.now(), null, "", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REFUND requires a payee");
        }

        @Test
        @DisplayName("carrying an invoiceId throws IllegalArgumentException even with a valid payee")
        void withInvoiceIdThrows() {
            assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "REFUND",
                    new BigDecimal("300.00"), LocalDate.now(), invoiceId, "Client themselves", null, capturedBy, "Jane Attorney", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REFUND must not carry an invoiceId");
        }

        @Test
        @DisplayName("increasesTrustBalance() is false")
        void increasesTrustBalanceIsFalse() {
            LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "REFUND",
                    new BigDecimal("100.00"), LocalDate.now(), null, "Client", null, capturedBy, "Jane Attorney", null);
            assertThat(tx.increasesTrustBalance()).isFalse();
        }
    }

    // ── Unknown type ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an unrecognised transaction type throws IllegalArgumentException")
    void unknownTypeThrows() {
        assertThatThrownBy(() -> LpTrustTransaction.create(tenantId, clientId, matterId, "WITHDRAWAL",
                new BigDecimal("100.00"), LocalDate.now(), null, null, null, capturedBy, "Jane Attorney", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown trust transaction type");
    }

    @Test
    @DisplayName("createdAt is stamped and captor fields are preserved verbatim")
    void createdAtStampedAndCaptorFieldsPreserved() {
        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, matterId, "RECEIPT",
                new BigDecimal("100.00"), LocalDate.now(), null, null, "Bank ref", capturedBy, "Jane Attorney", "Some notes");

        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(tx.getCapturedBy()).isEqualTo(capturedBy);
        assertThat(tx.getCapturedByName()).isEqualTo("Jane Attorney");
        assertThat(tx.getReference()).isEqualTo("Bank ref");
        assertThat(tx.getNotes()).isEqualTo("Some notes");
        assertThat(tx.getTenantId()).isEqualTo(tenantId);
    }
}
