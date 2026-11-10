package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The tariff and minimum-charge versions of one warehouse, and the rules for choosing among them. Pure: no database,
 * so the pricing rules can be tested and read in one place.
 *
 * Rate order for an item on a day: item tariff, item's own rate field, client tariff, client's own rate field,
 * warehouse default tariff, warehouse profile rate. Within one scope the version with the latest effective date that
 * has started wins; for the same date the most recently created wins.
 */
final class WhseTariffBook {

    record TariffVersion(UUID id, UUID clientId, UUID itemId, BigDecimal rate, LocalDate effectiveFrom, Instant createdAt) {}
    record TermsVersion(UUID id, UUID clientId, BigDecimal minimumStorageCharge, LocalDate effectiveFrom, Instant createdAt) {}
    record Rate(BigDecimal rate, String source, UUID tariffId) {}

    static final int DAYS_PER_MONTH = 30;

    private final List<TariffVersion> tariffs;
    private final List<TermsVersion> terms;

    WhseTariffBook(List<TariffVersion> tariffs, List<TermsVersion> terms) {
        this.tariffs = List.copyOf(tariffs);
        this.terms = List.copyOf(terms);
    }

    /** Null when no rate is set anywhere. */
    Rate resolve(UUID clientId, UUID itemId, LocalDate day, BigDecimal itemField, BigDecimal clientField, BigDecimal profileField) {
        Optional<TariffVersion> itemTariff = latest(clientId, itemId, day);
        if (itemTariff.isPresent()) return new Rate(itemTariff.get().rate(), "ITEM_TARIFF", itemTariff.get().id());
        if (itemField != null) return new Rate(itemField, "ITEM", null);
        Optional<TariffVersion> clientTariff = latest(clientId, null, day);
        if (clientTariff.isPresent()) return new Rate(clientTariff.get().rate(), "CLIENT_TARIFF", clientTariff.get().id());
        if (clientField != null) return new Rate(clientField, "CLIENT", null);
        Optional<TariffVersion> defaultTariff = latest(null, null, day);
        if (defaultTariff.isPresent()) return new Rate(defaultTariff.get().rate(), "DEFAULT_TARIFF", defaultTariff.get().id());
        if (profileField != null) return new Rate(profileField, "PROFILE", null);
        return null;
    }

    private Optional<TariffVersion> latest(UUID clientId, UUID itemId, LocalDate day) {
        return tariffs.stream()
                .filter(t -> java.util.Objects.equals(t.clientId(), clientId) && java.util.Objects.equals(t.itemId(), itemId))
                .filter(t -> !t.effectiveFrom().isAfter(day))
                .max(Comparator.comparing(TariffVersion::effectiveFrom).thenComparing(TariffVersion::createdAt));
    }

    /** The client's own minimum if it has one in force, otherwise the warehouse default; zero when none. */
    BigDecimal minimumStorageCharge(UUID clientId, LocalDate day) {
        Optional<TermsVersion> own = latestTerms(clientId, day);
        if (own.isPresent()) return own.get().minimumStorageCharge();
        return latestTerms(null, day).map(TermsVersion::minimumStorageCharge).orElse(BigDecimal.ZERO);
    }

    private Optional<TermsVersion> latestTerms(UUID clientId, LocalDate day) {
        return terms.stream()
                .filter(t -> java.util.Objects.equals(t.clientId(), clientId))
                .filter(t -> !t.effectiveFrom().isAfter(day))
                .max(Comparator.comparing(TermsVersion::effectiveFrom).thenComparing(TermsVersion::createdAt));
    }

    /** A minimum is stated per 30 days; an invoice covering fewer or more days gets its share. */
    static BigDecimal proratedMinimum(BigDecimal minimumPer30Days, long daysInPeriod) {
        if (minimumPer30Days == null || minimumPer30Days.signum() <= 0 || daysInPeriod <= 0) return BigDecimal.ZERO;
        return minimumPer30Days.multiply(BigDecimal.valueOf(daysInPeriod))
                .divide(BigDecimal.valueOf(DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
    }
}
