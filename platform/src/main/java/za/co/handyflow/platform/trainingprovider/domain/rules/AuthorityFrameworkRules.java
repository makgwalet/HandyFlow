package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What an accreditation record should carry beyond its status: the authority's priority in the provider's own list, the dates it
 * gives, whether the record is current, transitional or historical, where the information came from, and the evidence behind it.
 * No regulatory content lives here. Nothing here says a provider is accredited: that is the status the provider recorded, checked
 * against the scope and the dates by {@link AccreditationRules}.
 */
public final class AuthorityFrameworkRules {

    /** The order authorities are listed in. This is the provider's build and display order, not a legal ranking. */
    public static final List<String> PRIORITY = List.of("QCTO", "SETA", "SAQA", "DHET", "UMALUSI", "CHE", "PROFESSIONAL_BODY", "OTHER");
    public static final Set<String> CURRENCIES = Set.of("CURRENT", "TRANSITIONAL", "HISTORICAL");
    public static final Set<String> SOURCE_TYPES = Set.of("LETTER", "CERTIFICATE", "REGISTER_ENTRY", "NOTICE", "EMAIL", "WEBSITE", "OTHER");
    public static final Set<String> EVIDENCE_KINDS = Set.of("LETTER", "CERTIFICATE", "REGISTER_ENTRY", "NOTICE", "EMAIL", "OTHER");
    public static final int MIN_NOTE = 10;
    public static final int MIN_SOURCE = 5;

    private AuthorityFrameworkRules() {
    }

    public static int rank(String authorityType) {
        int i = PRIORITY.indexOf(authorityType);
        return i < 0 ? PRIORITY.size() : i;
    }

    private static boolean has(String s, int n) {
        return s != null && s.trim().length() >= n;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Why this change of currency is refused, or null. A transitional or historical record says why; a historical one says when it stopped applying. */
    public static String currencyProblem(String currency, String note, LocalDate endedOn, UUID supersededBy, UUID self, LocalDate approvalDate,
                                         LocalDate today) {
        if (currency == null || !CURRENCIES.contains(currency)) {
            return "Choose current, transitional or historical";
        }
        if ("CURRENT".equals(currency)) {
            return null;
        }
        if (!has(note, MIN_NOTE)) {
            return "Say why this is " + currency.toLowerCase() + ", in at least " + MIN_NOTE + " characters";
        }
        if ("TRANSITIONAL".equals(currency)) {
            return null;
        }
        if (endedOn == null) {
            return "Enter the date this stopped applying";
        }
        if (today != null && endedOn.isAfter(today)) {
            return "The end date cannot be in the future. Leave it current until it ends";
        }
        if (approvalDate != null && endedOn.isBefore(approvalDate)) {
            return "It cannot have ended before it was approved";
        }
        if (supersededBy != null && supersededBy.equals(self)) {
            return "A record cannot replace itself";
        }
        return null;
    }

    /** The source is optional, but if any of it is given the type and a reference are needed. */
    public static String sourceProblem(String type, String reference, LocalDate versionDate, String location, LocalDate today) {
        if (blank(type) && blank(reference) && versionDate == null && blank(location)) {
            return null;
        }
        if (blank(type) || !SOURCE_TYPES.contains(type)) {
            return "Say what kind of document the source is";
        }
        if (!has(reference, MIN_SOURCE)) {
            return "Give the document or reference the information came from (at least " + MIN_SOURCE + " characters)";
        }
        if (versionDate != null && today != null && versionDate.isAfter(today)) {
            return "The version date of the source cannot be in the future";
        }
        return null;
    }

    public static String datesProblem(LocalDate effectiveFrom, LocalDate approvalDate, LocalDate expiry, LocalDate reviewDue) {
        if (effectiveFrom != null && expiry != null && expiry.isBefore(effectiveFrom)) {
            return "The expiry date cannot be before the date it takes effect";
        }
        LocalDate start = effectiveFrom != null ? effectiveFrom : approvalDate;
        if (start != null && reviewDue != null && reviewDue.isBefore(start)) {
            return "The review date cannot be before the approval takes effect";
        }
        return null;
    }

    public static String evidenceProblem(String kind, String description, LocalDate receivedOn, LocalDate documentDate, LocalDate today) {
        if (kind == null || !EVIDENCE_KINDS.contains(kind)) {
            return "Say what kind of document this is";
        }
        if (!has(description, MIN_SOURCE)) {
            return "Describe the document, in at least " + MIN_SOURCE + " characters";
        }
        if (receivedOn == null) {
            return "Enter the date it was received";
        }
        if (today != null && receivedOn.isAfter(today)) {
            return "The received date cannot be in the future";
        }
        if (documentDate != null && documentDate.isAfter(receivedOn)) {
            return "The document cannot be dated after the day it was received";
        }
        return null;
    }

    /** Why this decision on a piece of evidence is refused, or null. Someone else verifies it, or the same person says why alone. */
    public static String evidenceDecisionProblem(String current, String to, UUID addedBy, UUID decider, String note) {
        if (!"PENDING".equals(current) && !("VERIFIED".equals(current) && "WITHDRAWN".equals(to))) {
            return "This evidence has already been decided";
        }
        if (!Set.of("VERIFIED", "REJECTED", "WITHDRAWN").contains(to)) {
            return "Choose verified, rejected or withdrawn";
        }
        if ("VERIFIED".equals(to)) {
            if (decider != null && decider.equals(addedBy) && !has(note, MIN_NOTE)) {
                return "A second person should verify this. If no one else can, say why in at least " + MIN_NOTE + " characters";
            }
            return null;
        }
        return has(note, MIN_NOTE) ? null : "Say why, in at least " + MIN_NOTE + " characters";
    }

    /**
     * Things a person should look at on one accreditation. These are prompts, not findings: none says the provider is or is not accredited.
     */
    public static List<String> flags(String effectiveStatus, String currency, int verifiedEvidence, int pendingEvidence, LocalDate reviewDue, LocalDate today) {
        List<String> out = new ArrayList<>();
        boolean approval = Set.of("APPROVED", "CONDITIONAL_APPROVAL", "RENEWAL_PENDING").contains(effectiveStatus);
        if ("EXPIRED".equals(effectiveStatus)) {
            out.add("EXPIRED");
        }
        if ("TRANSITIONAL".equals(currency)) {
            out.add("TRANSITIONAL");
        }
        if (approval && "CURRENT".equals(currency) && verifiedEvidence == 0) {
            out.add("NO_VERIFIED_EVIDENCE");
        }
        if (pendingEvidence > 0) {
            out.add("EVIDENCE_PENDING");
        }
        if (reviewDue != null && today != null && !"HISTORICAL".equals(currency) && reviewDue.isBefore(today)) {
            out.add("REVIEW_OVERDUE");
        }
        return out;
    }
}
