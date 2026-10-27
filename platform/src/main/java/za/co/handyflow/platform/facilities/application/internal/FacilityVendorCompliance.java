package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Whether a vendor's paperwork lets it receive work. Pure: no persistence, no clock.
 * <p>
 * A vendor needs one document in force for each required type ({@link #REQUIRED}); where several exist the one
 * expiring last counts, so renewing a document before the old one lapses never leaves a gap. Status:
 * NON_COMPLIANT (a required type is missing or lapsed), EXPIRING_SOON (all present, but one required document
 * expires within {@link #WARNING_DAYS} days), COMPLIANT.
 */
public final class FacilityVendorCompliance {

    public static final List<String> REQUIRED = List.of("PUBLIC_LIABILITY_INSURANCE", "COIDA_LETTER", "TAX_CLEARANCE");
    public static final int WARNING_DAYS = 30;

    private FacilityVendorCompliance() {}

    public record Expiring(String docType, LocalDate expiryDate, long daysLeft) {}

    public record Result(String status, List<String> missing, List<String> lapsed, List<Expiring> expiring) {
        public boolean isNonCompliant() { return "NON_COMPLIANT".equals(status); }

        /** Human-readable reasons the vendor cannot receive work; empty when compliant. */
        public List<String> problems() {
            List<String> out = new ArrayList<>();
            for (String t : missing) out.add("no " + t + " on file");
            for (String t : lapsed) out.add(t + " has expired");
            return out;
        }
    }

    public static Result evaluate(Collection<FacilityVendorDocument> docs, LocalDate today) {
        List<String> missing = new ArrayList<>();
        List<String> lapsed = new ArrayList<>();
        List<Expiring> expiring = new ArrayList<>();
        for (String type : REQUIRED) {
            List<FacilityVendorDocument> ofType = docs.stream()
                    .filter(d -> type.equals(d.getDocType()) && !"REVOKED".equals(d.getStatus())).toList();
            if (ofType.isEmpty()) { missing.add(type); continue; }
            Optional<LocalDate> best = ofType.stream().map(FacilityVendorDocument::getExpiryDate)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder());
            if (best.isEmpty() || best.get().isBefore(today)) { lapsed.add(type); continue; }
            long daysLeft = ChronoUnit.DAYS.between(today, best.get());
            if (daysLeft <= WARNING_DAYS) expiring.add(new Expiring(type, best.get(), daysLeft));
        }
        String status = !missing.isEmpty() || !lapsed.isEmpty() ? "NON_COMPLIANT" : expiring.isEmpty() ? "COMPLIANT" : "EXPIRING_SOON";
        return new Result(status, missing, lapsed, expiring);
    }
}
