package za.co.handyflow.platform.property.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Lease.applyEscalation() and the null-preserving behaviour of
 * Lease.renew().
 * <p>
 * WHY applyEscalation() specifically?
 * <p>
 * The real implementation computes the escalation factor to 6 decimal
 * places, and only rounds the FINAL rent to 2dp:
 * <pre>
 *   factor  = 1 + (percentIncrease / 100), kept to 6dp
 *   newRent = (monthlyRent * factor), rounded to 2dp only at the end
 * </pre>
 * This matters more than it looks like it should. 8.5% — an entirely
 * ordinary SA rent escalation rate, not an edge case — becomes 0.085 as a
 * fraction. Rounding THAT to 2dp first (0.085 -> 0.09 under HALF_UP,
 * since the third decimal is a 5) inflates every escalated rent by
 * roughly half a percent before the multiplication even happens. Verified
 * independently below: for a R8,500/mo lease, the correct 8.5% escalation
 * is R9,222.50 — a naive "round the factor to 2dp first" implementation
 * would produce R9,265.00 instead. That's not rounding noise, it's a real
 * billing error, and it would hit the single most common escalation rate
 * in this market first, not some rare edge case.
 * <p>
 * Every expected value was computed independently in Python's decimal
 * module (ROUND_HALF_UP) before being written here, and cross-checked
 * against what a plausible "simplified" regression would produce instead
 * — not derived by running this code and trusting its own output.
 */
class LeaseEscalationTest {

    private Lease newLease(String monthlyRent) {
        return Lease.create(
                TenantId.of(UUID.randomUUID().toString()),
                UUID.randomUUID(), null,
                "Test Lessee", null, "lessee@example.com", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal(monthlyRent), BigDecimal.ZERO,
                1, BigDecimal.ZERO);
    }

    @Test
    void standardEightPointFivePercent_theMostCommonSaEscalationRate() {
        Lease lease = newLease("8500.00");

        BigDecimal newRent = lease.applyEscalation(new BigDecimal("8.5"));

        // Independently verified: 8500 * 1.085 = 9222.50 exactly.
        // A naive 2dp-early-rounding implementation would produce
        // 9265.00 instead (0.085 rounds to 0.09 at 2dp) — a real, ~0.46%
        // billing inflation on the single most common escalation rate.
        assertThat(newRent).isEqualByComparingTo("9222.50");
        assertThat(lease.getMonthlyRent()).isEqualByComparingTo("9222.50");
    }

    @Test
    void anotherRealisticRent_sameEightPointFivePercentRate() {
        Lease lease = newLease("12320.00");

        BigDecimal newRent = lease.applyEscalation(new BigDecimal("8.5"));

        assertThat(newRent).isEqualByComparingTo("13367.20");
    }

    @Test
    void rentWithExistingCents_escalationCompoundsCorrectly() {
        Lease lease = newLease("9960.30");

        BigDecimal newRent = lease.applyEscalation(new BigDecimal("8.5"));

        assertThat(newRent).isEqualByComparingTo("10806.93");
    }

    @Test
    void rateThatDoesNotResolveCleanlyAtTwoDecimalPlaces() {
        // 1.333% is deliberately awkward — /100 gives 0.01333, which
        // genuinely needs more than 2 decimal places of factor precision
        // to land correctly. This is the case that most directly proves
        // the 6dp intermediate precision is doing real work, not just
        // matching an arbitrary implementation choice.
        Lease lease = newLease("85000.00");

        BigDecimal newRent = lease.applyEscalation(new BigDecimal("1.333"));

        assertThat(newRent).isEqualByComparingTo("86133.05");
    }

    @Test
    void zeroPercentEscalation_leavesRentUnchanged() {
        Lease lease = newLease("500.00");

        BigDecimal newRent = lease.applyEscalation(BigDecimal.ZERO);

        assertThat(newRent).isEqualByComparingTo("500.00");
    }

    @Test
    void renew_nullValues_preserveExistingRentAndEscalationRate() {
        // updateTerms()/renew() both null-guard every field individually
        // — if that guard were ever accidentally removed, calling renew()
        // with only a new end date (leaving rent/escalation as null,
        // meaning "don't change these") would silently wipe out the
        // tenant's actual rent to null instead of leaving it untouched.
        Lease lease = newLease("8500.00");
        LocalDate newEndDate = LocalDate.of(2027, 12, 31);

        lease.renew(newEndDate, null, null);

        assertThat(lease.getEndDate()).isEqualTo(newEndDate);
        assertThat(lease.getMonthlyRent()).isEqualByComparingTo("8500.00");
        assertThat(lease.getEscalationRate()).isEqualByComparingTo("0");
    }

    @Test
    void renew_withNewRent_actuallyUpdatesIt() {
        Lease lease = newLease("8500.00");

        lease.renew(LocalDate.of(2027, 12, 31), new BigDecimal("9222.50"), new BigDecimal("8.5"));

        assertThat(lease.getMonthlyRent()).isEqualByComparingTo("9222.50");
        assertThat(lease.getEscalationRate()).isEqualByComparingTo("8.5");
        // A renewal reactivates the lease even if it had expired — this
        // is the one unconditional field renew() always sets.
        assertThat(lease.getStatus()).isEqualTo("ACTIVE");
    }
}