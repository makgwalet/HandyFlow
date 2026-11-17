package za.co.handyflow.platform.supplychain.application.internal;

import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure supplier-compliance evaluation. For each REQUIRED document type the best ACTIVE document
 * (latest expiry; no expiry = never expires) decides the item status. A document is valid on its
 * expiry date and expired the day after.
 * Overall: NOT_REQUIRED (no required types configured) | NON_COMPLIANT (anything missing, expired
 * or — when verification is required — unverified) | EXPIRING (all valid, something inside the
 * warning window) | COMPLIANT.
 */
public final class SupplierComplianceRules {

    private SupplierComplianceRules() {}

    public enum ItemStatus { COMPLIANT, EXPIRING, EXPIRED, MISSING, UNVERIFIED }
    public enum Overall { NOT_REQUIRED, COMPLIANT, EXPIRING, NON_COMPLIANT }

    public record DocView(SupplierDocType type, LocalDate expiry, boolean verified) {}
    public record Item(SupplierDocType type, ItemStatus status, LocalDate expiry, Long daysToExpiry) {}
    public record Result(Overall overall, List<Item> items) {
        public List<Item> problems() {
            return items.stream().filter(i -> i.status() != ItemStatus.COMPLIANT && i.status() != ItemStatus.EXPIRING).toList();
        }
    }

    public static Result evaluate(Set<SupplierDocType> required, List<DocView> activeDocs, LocalDate today,
                                  int warnDays, boolean requireVerified) {
        if (required.isEmpty()) return new Result(Overall.NOT_REQUIRED, List.of());

        List<Item> items = new ArrayList<>();
        for (SupplierDocType type : new TreeSet<>(required)) {
            DocView best = activeDocs.stream().filter(d -> d.type() == type)
                    .max(Comparator.comparing(d -> d.expiry() == null ? LocalDate.MAX : d.expiry()))
                    .orElse(null);
            if (best == null) { items.add(new Item(type, ItemStatus.MISSING, null, null)); continue; }

            Long days = best.expiry() == null ? null : ChronoUnit.DAYS.between(today, best.expiry());
            ItemStatus st;
            if (days != null && days < 0) st = ItemStatus.EXPIRED;
            else if (requireVerified && !best.verified()) st = ItemStatus.UNVERIFIED;
            else if (days != null && days <= warnDays) st = ItemStatus.EXPIRING;
            else st = ItemStatus.COMPLIANT;
            items.add(new Item(type, st, best.expiry(), days));
        }

        Overall overall = Overall.COMPLIANT;
        for (Item i : items) {
            if (i.status() == ItemStatus.MISSING || i.status() == ItemStatus.EXPIRED || i.status() == ItemStatus.UNVERIFIED) {
                overall = Overall.NON_COMPLIANT;
                break;
            }
            if (i.status() == ItemStatus.EXPIRING) overall = Overall.EXPIRING;
        }
        return new Result(overall, items);
    }
}
