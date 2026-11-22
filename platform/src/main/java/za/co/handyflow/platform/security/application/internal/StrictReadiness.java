package za.co.handyflow.platform.security.application.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where does a guard stand if firearm issue switched from legacy to strict competency checking? Pure.
 * (Legacy: an uncategorised certificate or the old guard-level date covers every firearm. Strict: only a verified
 * certificate that names a category counts, and only for that category. See {@link CompetencyChecks}.)
 */
final class StrictReadiness {

    enum Status { READY_ALL, READY_PARTIAL, NEEDS_CATEGORY, NEEDS_VERIFICATION, LEGACY_ONLY, EXPIRED_ONLY }

    record Cert(String category, LocalDate expiry, boolean verified) {}
    record Result(Status status, Set<String> covered, String hint) {}

    private StrictReadiness() {}

    /** Statuses that can be issued a firearm today (legacy) but would be refused under strict mode. */
    static boolean blockedByStrict(Status s) {
        return s == Status.NEEDS_CATEGORY || s == Status.NEEDS_VERIFICATION || s == Status.LEGACY_ONLY;
    }

    static Result classify(List<Cert> certs, boolean legacyGuardLevelValid, LocalDate today) {
        List<Cert> inForce = certs.stream().filter(c -> c.expiry() != null && !c.expiry().isBefore(today)).toList();

        Set<String> covered = new TreeSet<>();
        for (Cert c : inForce) if (c.verified() && c.category() != null) covered.add(c.category());
        if (covered.containsAll(CompetencyChecks.CATEGORIES)) return new Result(Status.READY_ALL, covered, "");
        if (!covered.isEmpty()) {
            return new Result(Status.READY_PARTIAL, covered, "Covers " + String.join(", ", covered).toLowerCase()
                    + " only. Add the other categories if this guard will carry them.");
        }
        if (inForce.stream().anyMatch(c -> c.category() == null)) {
            return new Result(Status.NEEDS_CATEGORY, covered,
                    "Set the firearm category on this guard's competency (Skills tab) and verify it.");
        }
        if (!inForce.isEmpty()) {
            return new Result(Status.NEEDS_VERIFICATION, covered,
                    "Attach the certificate and verify it (Skills tab).");
        }
        if (legacyGuardLevelValid) {
            return new Result(Status.LEGACY_ONLY, covered,
                    "Only the old guard-level competency date is on file. Add a firearm competency with a category on the Skills tab.");
        }
        return new Result(Status.EXPIRED_ONLY, covered, "The firearm competency has expired. Renew it on the Skills tab.");
    }
}
