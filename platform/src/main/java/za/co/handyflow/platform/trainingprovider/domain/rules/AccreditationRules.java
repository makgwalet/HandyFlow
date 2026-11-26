package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for provider accreditation. No regulatory content lives here:
 * the provider records its own authorities, numbers and dates, and these
 * rules only decide whether what was recorded supports a given claim.
 * An application that is merely submitted never counts as approval.
 */
public final class AccreditationRules {

    private AccreditationRules() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String APPLICATION_PREPARATION = "APPLICATION_PREPARATION";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String MORE_INFORMATION_REQUIRED = "MORE_INFORMATION_REQUIRED";
    public static final String CONDITIONAL_APPROVAL = "CONDITIONAL_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String RENEWAL_PENDING = "RENEWAL_PENDING";

    public static final int MIN_REASON = 10;
    public static final int MIN_SOURCE = 5;

    private static final Map<String, Set<String>> NEXT = Map.ofEntries(
            Map.entry(DRAFT, Set.of(APPLICATION_PREPARATION, APPROVED, CONDITIONAL_APPROVAL, WITHDRAWN)),
            Map.entry(APPLICATION_PREPARATION, Set.of(SUBMITTED, WITHDRAWN)),
            Map.entry(SUBMITTED, Set.of(UNDER_REVIEW, MORE_INFORMATION_REQUIRED, WITHDRAWN)),
            Map.entry(UNDER_REVIEW, Set.of(APPROVED, CONDITIONAL_APPROVAL, MORE_INFORMATION_REQUIRED, REJECTED, WITHDRAWN)),
            Map.entry(MORE_INFORMATION_REQUIRED, Set.of(SUBMITTED, UNDER_REVIEW, WITHDRAWN)),
            Map.entry(CONDITIONAL_APPROVAL, Set.of(APPROVED, SUSPENDED, RENEWAL_PENDING, EXPIRED, WITHDRAWN)),
            Map.entry(APPROVED, Set.of(SUSPENDED, RENEWAL_PENDING, EXPIRED, WITHDRAWN)),
            Map.entry(RENEWAL_PENDING, Set.of(APPROVED, CONDITIONAL_APPROVAL, SUSPENDED, EXPIRED, REJECTED, WITHDRAWN)),
            Map.entry(SUSPENDED, Set.of(APPROVED, CONDITIONAL_APPROVAL, EXPIRED, WITHDRAWN)),
            Map.entry(EXPIRED, Set.of(RENEWAL_PENDING, APPROVED, CONDITIONAL_APPROVAL, WITHDRAWN)),
            Map.entry(REJECTED, Set.of()),
            Map.entry(WITHDRAWN, Set.of()));

    private static final Set<String> NEEDS_REASON = Set.of(REJECTED, SUSPENDED, WITHDRAWN, MORE_INFORMATION_REQUIRED, EXPIRED);
    private static final Set<String> APPROVAL_STATES = Set.of(APPROVED, CONDITIONAL_APPROVAL);
    private static final Set<String> IN_FORCE = Set.of(APPROVED, CONDITIONAL_APPROVAL, RENEWAL_PENDING);
    private static final Set<String> EDITABLE = Set.of(DRAFT, APPLICATION_PREPARATION, SUBMITTED, UNDER_REVIEW, MORE_INFORMATION_REQUIRED);

    public static boolean isKnown(String status) {
        return NEXT.containsKey(status);
    }

    public static boolean isApprovalState(String status) {
        return APPROVAL_STATES.contains(status);
    }

    /** Number, title, dates and conditions may only be edited before an approval is recorded; after that a transition carries the change. */
    public static boolean detailsEditable(String status) {
        return EDITABLE.contains(status);
    }

    public static boolean isTerminal(String status) {
        return REJECTED.equals(status) || WITHDRAWN.equals(status);
    }

    public static Set<String> nextStates(String from) {
        return NEXT.getOrDefault(from, Set.of());
    }

    /**
     * The status to show and to rely on today. A recorded approval whose
     * expiry date has passed is expired, whether or not anyone has moved it.
     */
    public static String effectiveStatus(String status, LocalDate expiry, LocalDate today) {
        if (IN_FORCE.contains(status) && expiry != null && today != null && expiry.isBefore(today)) {
            return EXPIRED;
        }
        return status;
    }

    /**
     * Why a status change is refused, or null if it may go ahead.
     * An approval needs the authority's number, an approval date, a
     * verification source and, unless a second person records it, a stated reason.
     */
    public static String transitionProblem(String from, String to, String reason, String number, LocalDate approvalDate,
                                           LocalDate expiryDate, String conditions, String verificationSource,
                                           UUID actor, UUID creator, String overrideReason, LocalDate today) {
        if (!isKnown(to)) {
            return "Unknown status " + to;
        }
        if (!nextStates(from).contains(to)) {
            return "An accreditation cannot move from " + label(from) + " to " + label(to);
        }
        if (NEEDS_REASON.contains(to) && !longEnough(reason, MIN_REASON)) {
            return "Say why, in at least " + MIN_REASON + " characters";
        }
        if (APPROVAL_STATES.contains(to)) {
            if (number == null || number.isBlank()) {
                return "Enter the accreditation number given by the authority";
            }
            if (approvalDate == null) {
                return "Enter the approval date shown on the authority's letter or register";
            }
            if (today != null && approvalDate.isAfter(today)) {
                return "The approval date cannot be in the future";
            }
            if (expiryDate != null && expiryDate.isBefore(approvalDate)) {
                return "The expiry date cannot be before the approval date";
            }
            if (expiryDate != null && today != null && expiryDate.isBefore(today)) {
                return "That approval has already expired. Record it as expired, or enter the renewed expiry date";
            }
            if (!longEnough(verificationSource, MIN_SOURCE)) {
                return "Say where the approval was verified, for example the authority's letter or register";
            }
            if (CONDITIONAL_APPROVAL.equals(to) && !longEnough(conditions, MIN_REASON)) {
                return "A conditional approval needs its conditions written down";
            }
            if (actor != null && actor.equals(creator) && !longEnough(overrideReason, MIN_REASON)) {
                return "A second person should confirm an approval. If no one else can, say why in at least "
                        + MIN_REASON + " characters";
            }
        }
        return null;
    }

    public enum Outcome {
        COVERED, COVERED_WITH_CONDITIONS, COVERED_RENEWAL_PENDING,
        NO_SCOPE, SITE_NOT_COVERED, SCOPE_WITHDRAWN, SCOPE_NOT_IN_FORCE,
        NOT_APPROVED, SUSPENDED, EXPIRED
    }

    public record Coverage(Outcome outcome, boolean covered, String message) {
    }

    /** One scope row, as the rules need it. */
    public record ScopeFacts(String accreditationStatus, LocalDate accreditationExpiry, String scopeStatus,
                             LocalDate validFrom, LocalDate validTo, UUID siteId, String currency, LocalDate effectiveFrom,
                             LocalDate endedOn) {
        public ScopeFacts(String accreditationStatus, LocalDate accreditationExpiry, String scopeStatus,
                          LocalDate validFrom, LocalDate validTo, UUID siteId) {
            this(accreditationStatus, accreditationExpiry, scopeStatus, validFrom, validTo, siteId, "CURRENT", null, null);
        }
    }

    /**
     * Whether one scope row lets the provider deliver on a given day at a given site.
     * Covered only when the accreditation is in force, the scope is live and in date,
     * and the scope names exactly this site.
     */
    public static Coverage evaluate(ScopeFacts s, UUID requestedSite, LocalDate day) {
        if (requestedSite == null || !requestedSite.equals(s.siteId())) {
            return new Coverage(Outcome.SITE_NOT_COVERED, false, "The approval does not cover this delivery site");
        }
        if ("WITHDRAWN".equals(s.scopeStatus())) {
            return new Coverage(Outcome.SCOPE_WITHDRAWN, false, "This programme was withdrawn from the approval");
        }
        if ((s.validFrom() != null && day.isBefore(s.validFrom())) || (s.validTo() != null && day.isAfter(s.validTo()))) {
            return new Coverage(Outcome.SCOPE_NOT_IN_FORCE, false, "The approval for this programme is not in force on that date");
        }
        if (s.effectiveFrom() != null && day.isBefore(s.effectiveFrom())) {
            return new Coverage(Outcome.SCOPE_NOT_IN_FORCE, false, "The approval had not taken effect on that date");
        }
        if ("HISTORICAL".equals(s.currency()) && s.endedOn() != null && day.isAfter(s.endedOn())) {
            return new Coverage(Outcome.SCOPE_NOT_IN_FORCE, false, "This is a historical approval that stopped applying on " + s.endedOn());
        }
        boolean transitional = "TRANSITIONAL".equals(s.currency());
        String effective = effectiveStatus(s.accreditationStatus(), s.accreditationExpiry(), day);
        return switch (effective) {
            case APPROVED -> transitional
                    ? new Coverage(Outcome.COVERED_WITH_CONDITIONS, true, "Covered under a transitional arrangement. Read the note on the accreditation")
                    : new Coverage(Outcome.COVERED, true, "Covered by a current approval");
            case CONDITIONAL_APPROVAL -> new Coverage(Outcome.COVERED_WITH_CONDITIONS, true,
                    transitional ? "Covered under a transitional arrangement, and the approval has conditions that must be met"
                            : "Covered, but the approval has conditions that must be met");
            case RENEWAL_PENDING -> new Coverage(Outcome.COVERED_RENEWAL_PENDING, true,
                    "Covered until the current expiry date. A renewal is pending, not yet approved");
            case EXPIRED -> new Coverage(Outcome.EXPIRED, false, "The approval has expired");
            case SUSPENDED -> new Coverage(Outcome.SUSPENDED, false, "The approval is suspended");
            default -> new Coverage(Outcome.NOT_APPROVED, false,
                    "The accreditation is " + label(effective) + ". It is not an approval");
        };
    }

    private static final List<Outcome> BEST_FIRST = List.of(
            Outcome.COVERED, Outcome.COVERED_WITH_CONDITIONS, Outcome.COVERED_RENEWAL_PENDING,
            Outcome.SCOPE_NOT_IN_FORCE, Outcome.EXPIRED, Outcome.SUSPENDED, Outcome.NOT_APPROVED,
            Outcome.SCOPE_WITHDRAWN, Outcome.SITE_NOT_COVERED, Outcome.NO_SCOPE);

    /**
     * The most favourable honest answer across every scope row found for a
     * programme at a site: covered beats a lapsed approval, which beats "not
     * covered at this site", which beats "no scope at all".
     */
    public static Coverage best(List<ScopeFacts> scopes, UUID requestedSite, LocalDate day) {
        if (scopes == null || scopes.isEmpty()) {
            return new Coverage(Outcome.NO_SCOPE, false, "No accreditation scope has been recorded for this programme");
        }
        Coverage best = null;
        for (ScopeFacts s : scopes) {
            Coverage c = evaluate(s, requestedSite, day);
            if (best == null || BEST_FIRST.indexOf(c.outcome()) < BEST_FIRST.indexOf(best.outcome())) {
                best = c;
            }
        }
        return best;
    }

    private static boolean longEnough(String s, int n) {
        return s != null && s.trim().length() >= n;
    }

    private static String label(String status) {
        return status == null ? "" : status.toLowerCase().replace('_', ' ');
    }
}
