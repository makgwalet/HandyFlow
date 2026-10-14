package za.co.handyflow.platform.debtcollection.application.internal;

import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Pure rules for debtor disputes (finance F16). No framework, no database. */
public final class DisputeRules {

    private DisputeRules() {}

    public static final List<String> REASONS = List.of(
            "AMOUNT", "NOT_OWED", "ALREADY_PAID", "SERVICE_QUALITY", "WRONG_DEBTOR", "OTHER");
    public static final List<String> RESOLUTIONS = List.of("UPHELD", "PARTLY_UPHELD", "REJECTED", "WITHDRAWN");
    /** Business days allowed to respond to a dispute. */
    public static final int RESPONSE_DAYS = 10;
    public static final int MIN_TEXT = 10;

    private static final Set<CaseStatus> TERMINAL = Set.of(CaseStatus.CLOSED, CaseStatus.SETTLED, CaseStatus.WRITTEN_OFF);

    public static LocalDate responseDue(LocalDate raised, Set<LocalDate> holidays) {
        return BusinessDays.addBusinessDays(raised, RESPONSE_DAYS, holidays);
    }

    /** Null when the dispute may be opened. {@code invoiceId} may be null (case-level dispute). */
    public static String openProblem(CaseStatus caseStatus, String reason, String description, boolean invoiceOnCase,
                                     BigDecimal amountDisputed, BigDecimal invoiceOutstanding, boolean invoiceGiven) {
        if (TERMINAL.contains(caseStatus)) return "The case is " + caseStatus + "; a dispute cannot be opened on it.";
        if (reason == null || !REASONS.contains(reason)) return "Choose a reason for the dispute.";
        if (description == null || description.trim().length() < MIN_TEXT) {
            return "Describe the dispute in at least " + MIN_TEXT + " characters.";
        }
        if (invoiceGiven && !invoiceOnCase) return "That invoice is not linked to this case.";
        if (amountDisputed != null) {
            if (amountDisputed.signum() <= 0) return "The disputed amount must be more than zero.";
            if (invoiceOutstanding != null && amountDisputed.compareTo(invoiceOutstanding) > 0) {
                return "The disputed amount is more than the invoice still owes.";
            }
        }
        return null;
    }

    /** Case status after opening: collection pauses, except when already with legal (status kept, a flag is raised). */
    public static CaseStatus statusAfterOpen(CaseStatus current) {
        return current == CaseStatus.HANDED_TO_LEGAL ? current : CaseStatus.DISPUTED;
    }

    /** What to remember so resolution can put the case back. Null when we did not change the status. */
    public static CaseStatus statusToRemember(CaseStatus current) {
        return (current == CaseStatus.HANDED_TO_LEGAL || current == CaseStatus.DISPUTED) ? null : current;
    }

    /** Case status once a dispute is resolved: unchanged while others are open, else the remembered status. */
    public static CaseStatus statusAfterResolve(CaseStatus current, CaseStatus remembered, boolean otherOpenDisputes) {
        if (otherOpenDisputes || current != CaseStatus.DISPUTED) return current;
        if (remembered == null || remembered == CaseStatus.DISPUTED || TERMINAL.contains(remembered)) return CaseStatus.OPEN;
        return remembered;
    }

    public static String resolveProblem(String status, String resolution, String note,
                                        BigDecimal conceded, BigDecimal disputed) {
        if ("RESOLVED".equals(status)) return "This dispute is already resolved.";
        if (resolution == null || !RESOLUTIONS.contains(resolution)) return "Choose how the dispute was resolved.";
        if (note == null || note.trim().length() < MIN_TEXT) return "Explain the outcome in at least " + MIN_TEXT + " characters.";
        boolean concedes = "UPHELD".equals(resolution) || "PARTLY_UPHELD".equals(resolution);
        boolean hasAmount = conceded != null && conceded.signum() > 0;
        if (concedes && !hasAmount) return "Enter the amount conceded to the debtor.";
        if (!concedes && conceded != null && conceded.signum() != 0) return "No amount is conceded when the dispute is " + resolution + ".";
        if (hasAmount && disputed != null && conceded.compareTo(disputed) > 0) return "The amount conceded is more than the amount disputed.";
        if ("PARTLY_UPHELD".equals(resolution) && disputed != null && hasAmount && conceded.compareTo(disputed) == 0) {
            return "A partly upheld dispute concedes less than the full amount; choose UPHELD instead.";
        }
        return null;
    }

    public static String takeOnProblem(String status) {
        return "OPEN".equals(status) ? null : "Only an open dispute can be taken into review.";
    }

    public static boolean overdue(String status, LocalDate responseDue, LocalDate today) {
        return !"RESOLVED".equals(status) && responseDue != null && today.isAfter(responseDue);
    }
}
