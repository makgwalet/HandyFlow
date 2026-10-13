package za.co.handyflow.platform.clinic.application.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** How a patient's payment is split over the visits that still owe (patch 0190, CLINIC-DEC-005). Pure. */
final class PatientReceivableRules {
    private PatientReceivableRules() {}

    /** A claim whose patient portion is not fully paid yet. */
    record Open(UUID claimId, Instant createdAt, BigDecimal owed) {}

    record Part(UUID claimId, BigDecimal amount) {}

    /** The parts of the payment that go to a visit, and what is left on account. */
    record Split(List<Part> parts, BigDecimal onAccount) {
        BigDecimal allocated() { return parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** What a visit still owes from the patient: their portion less what has been linked to it, never below nothing. */
    static BigDecimal owed(BigDecimal patientPortion, BigDecimal linked) { return z(patientPortion).subtract(z(linked)).max(BigDecimal.ZERO); }

    /** What part of a payment has not been linked to a visit yet. */
    static BigDecimal unlinked(BigDecimal paymentAmount, BigDecimal linked) { return z(paymentAmount).subtract(z(linked)).max(BigDecimal.ZERO); }

    /** Oldest visit first, each up to what it owes; the rest stays on account. */
    static Split oldestFirst(BigDecimal amount, Collection<Open> open) {
        BigDecimal left = z(amount);
        List<Open> sorted = open.stream().filter(o -> z(o.owed()).signum() > 0)
                .sorted(Comparator.comparing(Open::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Open::claimId)).toList();
        List<Part> parts = new ArrayList<>();
        for (Open o : sorted) {
            if (left.signum() <= 0) break;
            BigDecimal take = left.min(o.owed());
            parts.add(new Part(o.claimId(), take));
            left = left.subtract(take);
        }
        return new Split(parts, left.max(BigDecimal.ZERO));
    }

    /** A chosen visit only, up to what it owes; the rest stays on account. A visit that owes nothing (or is not in the list) takes nothing. */
    static Split toVisit(UUID claimId, BigDecimal amount, Collection<Open> open) {
        BigDecimal left = z(amount);
        for (Open o : open) {
            if (!o.claimId().equals(claimId) || z(o.owed()).signum() <= 0) continue;
            BigDecimal take = left.min(o.owed());
            return new Split(take.signum() > 0 ? List.of(new Part(claimId, take)) : List.of(), left.subtract(take).max(BigDecimal.ZERO));
        }
        return new Split(List.of(), left.max(BigDecimal.ZERO));
    }
}
