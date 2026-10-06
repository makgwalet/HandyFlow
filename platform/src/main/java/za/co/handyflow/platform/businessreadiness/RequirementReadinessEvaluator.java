package za.co.handyflow.platform.businessreadiness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Judges requirements against the registrations and documents a business has. Pure and stateless: the same inputs always give the same answer, so it is tested without
 * a database and used identically for a tenant's own tenders and for a client's.
 * <p>
 * Registration validity: ACTIVE with no expiry, or ACTIVE with an expiry on or after the as-of date. EXPIRED, LAPSED and ACTIVE-but-past-expiry are expired; PENDING is
 * pending; NOT_APPLICABLE registrations are ignored as if absent. The current state of the records is used; there is no history of past states, so a tender that already
 * closed is judged on today's records, which is why the panel is for tenders still in preparation and the submission snapshot remains the record of what was submitted.
 */
public final class RequirementReadinessEvaluator {

    /** Matches the 30-day warning window of ComplianceExpiryScheduler. */
    public static final int EXPIRING_SOON_DAYS = 30;

    private RequirementReadinessEvaluator() {}

    public static ReadinessAssessment evaluate(LocalDate today, LocalDate asOf, String asOfBasis, List<RequirementToEvaluate> requirements,
                                               List<RegistrationFact> registrations, List<DocumentFact> documents) {
        List<ReadinessItem> items = new ArrayList<>();
        for (RequirementToEvaluate r : requirements) items.add(evaluateOne(today, asOf, asOfBasis, r, registrations, documents));
        return new ReadinessAssessment(asOf, asOfBasis, List.copyOf(items), summarise(items));
    }

    private static ReadinessItem evaluateOne(LocalDate today, LocalDate asOf, String basis, RequirementToEvaluate r, List<RegistrationFact> regs, List<DocumentFact> docs) {
        if ("NOT_APPLICABLE".equals(r.manualStatus())) {
            return item(r, ReadinessResult.NOT_APPLICABLE, "You marked this not applicable", null, false);
        }
        RequirementRule rule = r.rule();
        if (rule == null || rule.isEmpty()) {
            return item(r, ReadinessResult.NOT_EVALUATED, r.requirementId() == null
                    ? "Not linked to a tracked requirement, so nothing to check it against" : "No evidence rule is set on this requirement", null, false);
        }

        Part reg = rule.needsRegistration() ? checkRegistration(today, asOf, rule, regs) : null;
        Part doc = rule.needsDocument() ? checkDocument(today, asOf, rule, docs) : null;

        Part worst = worse(reg, doc);
        ReadinessResult result = worst.result();
        String detail = joinDetails(reg, doc);
        LocalDate expiresOn = null;
        if (result == ReadinessResult.MET) {
            expiresOn = earliest(reg == null ? null : reg.expiresOn(), doc == null ? null : doc.expiresOn());
        } else if (result == ReadinessResult.EXPIRED) {
            expiresOn = worst.expiresOn();
        }
        boolean soon = result == ReadinessResult.MET && expiresOn != null && !expiresOn.isAfter(asOf.plusDays(EXPIRING_SOON_DAYS));
        return item(r, result, detail, expiresOn, soon);
    }

    // ---- registration ------------------------------------------------------------------------------------------------

    private static Part checkRegistration(LocalDate today, LocalDate asOf, RequirementRule rule, List<RegistrationFact> regs) {
        String what = describe(rule);
        List<RegistrationFact> matching = regs.stream().filter(g -> !"NOT_APPLICABLE".equals(g.status()) && matches(rule.authority(), g.authority()) && matches(rule.registrationType(), g.registrationType())).toList();
        if (matching.isEmpty()) return new Part(ReadinessResult.MISSING, "No " + what + " registration is recorded", null);

        List<RegistrationFact> valid = matching.stream().filter(g -> isValid(g, asOf)).toList();
        if (!valid.isEmpty()) {
            LocalDate best = null; boolean noExpiry = false;
            for (RegistrationFact g : valid) { if (g.expiryDate() == null) noExpiry = true; else if (best == null || g.expiryDate().isAfter(best)) best = g.expiryDate(); }
            LocalDate expiry = noExpiry ? null : best;
            return new Part(ReadinessResult.MET, what + " registration is valid" + (expiry == null ? "" : ", expires " + expiry), expiry);
        }
        if (matching.stream().anyMatch(g -> "PENDING".equals(g.status()))) return new Part(ReadinessResult.PENDING, what + " registration is still pending", null);

        LocalDate latest = matching.stream().map(RegistrationFact::expiryDate).filter(d -> d != null).max(Comparator.naturalOrder()).orElse(null);
        boolean lapsedOnly = matching.stream().allMatch(g -> "LAPSED".equals(g.status()));
        String detail;
        if (lapsedOnly) detail = what + " registration has lapsed";
        else if (latest == null) detail = what + " registration is marked expired";
        else if (latest.isBefore(asOf)) {
            // its expiry date is before the date that matters: already past, or (for a tender) still to come but before the closing date
            detail = latest.isBefore(today) ? what + " registration expired on " + latest : what + " registration expires " + latest + ", before the closing date " + asOf;
        } else {
            detail = what + " registration is marked expired (expiry date " + latest + ")";       // someone marked it expired although its date has not passed
        }
        return new Part(ReadinessResult.EXPIRED, detail, latest);
    }

    private static boolean isValid(RegistrationFact g, LocalDate asOf) {
        return "ACTIVE".equals(g.status()) && (g.expiryDate() == null || !g.expiryDate().isBefore(asOf));
    }

    // ---- document ------------------------------------------------------------------------------------------------------

    private static Part checkDocument(LocalDate today, LocalDate asOf, RequirementRule rule, List<DocumentFact> docs) {
        String type = rule.documentType().trim();
        List<DocumentFact> matching = docs.stream().filter(d -> matches(rule.documentType(), d.documentType())).toList();
        if (matching.isEmpty()) return new Part(ReadinessResult.MISSING, "No " + type + " document is uploaded", null);

        List<DocumentFact> unexpired = matching.stream().filter(d -> d.expiryDate() == null || !d.expiryDate().isBefore(asOf)).toList();
        List<DocumentFact> verified = unexpired.stream().filter(DocumentFact::verified).toList();
        if (!verified.isEmpty()) {
            LocalDate best = null; boolean noExpiry = false;
            for (DocumentFact d : verified) { if (d.expiryDate() == null) noExpiry = true; else if (best == null || d.expiryDate().isAfter(best)) best = d.expiryDate(); }
            LocalDate expiry = noExpiry ? null : best;
            return new Part(ReadinessResult.MET, type + " document is verified" + (expiry == null ? "" : ", expires " + expiry), expiry);
        }
        if (!unexpired.isEmpty()) return new Part(ReadinessResult.PENDING, type + " document is uploaded but not yet verified", null);

        LocalDate latest = matching.stream().map(DocumentFact::expiryDate).filter(d -> d != null).max(Comparator.naturalOrder()).orElse(null);
        String detail = latest == null ? type + " document is not usable"
                : !latest.isBefore(today) ? type + " document expires " + latest + ", before the closing date " + asOf : type + " document expired on " + latest;
        return new Part(ReadinessResult.EXPIRED, detail, latest);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private record Part(ReadinessResult result, String detail, LocalDate expiresOn) {}

    private static Part worse(Part a, Part b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.result().severity() > a.result().severity() ? b : a;
    }

    private static String joinDetails(Part a, Part b) {
        if (a == null) return b.detail();
        if (b == null) return a.detail();
        return a.detail() + "; " + b.detail();
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /** A blank wanted value matches anything; otherwise equal ignoring case and surrounding spaces. */
    private static boolean matches(String wanted, String actual) {
        if (RequirementRule.blank(wanted)) return true;
        return actual != null && wanted.trim().equalsIgnoreCase(actual.trim());
    }

    private static String describe(RequirementRule rule) {
        String a = RequirementRule.blank(rule.authority()) ? "" : rule.authority().trim();
        String t = RequirementRule.blank(rule.registrationType()) ? "" : rule.registrationType().trim();
        return (a + " " + t).trim();
    }

    private static ReadinessItem item(RequirementToEvaluate r, ReadinessResult result, String detail, LocalDate expiresOn, boolean soon) {
        String manual = r.manualStatus();
        boolean differs = ("MET".equals(manual) && (result == ReadinessResult.MISSING || result == ReadinessResult.EXPIRED || result == ReadinessResult.PENDING))
                || ("MISSING".equals(manual) && result == ReadinessResult.MET);
        return new ReadinessItem(r.requirementId(), r.label(), manual, result, detail, expiresOn, soon, differs, r.newerVersionAvailable());
    }

    private static ReadinessSummary summarise(List<ReadinessItem> items) {
        int met = 0, missing = 0, expired = 0, pending = 0, notEvaluated = 0, notApplicable = 0, soon = 0, differ = 0;
        for (ReadinessItem i : items) {
            switch (i.result()) {
                case MET -> met++;
                case MISSING -> missing++;
                case EXPIRED -> expired++;
                case PENDING -> pending++;
                case NOT_EVALUATED -> notEvaluated++;
                case NOT_APPLICABLE -> notApplicable++;
            }
            if (i.expiringSoon()) soon++;
            if (i.differsFromManualStatus()) differ++;
        }
        return new ReadinessSummary(items.size(), met, missing, expired, pending, notEvaluated, notApplicable, soon, differ);
    }
}
