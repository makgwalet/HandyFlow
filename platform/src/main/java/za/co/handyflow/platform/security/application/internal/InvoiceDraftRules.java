package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;

/**
 * Pure rules for invoice drafts. A draft is a frozen copy of the billing preview for one site and month. It is not an invoice.
 * DRAFT can be approved or voided; APPROVED can only be voided (with a reason); VOID is final.
 */
final class InvoiceDraftRules {

    static final String DRAFT = "DRAFT";
    static final String APPROVED = "APPROVED";
    static final String VOID = "VOID";
    static final int MIN_REASON = 5;
    static final int MAX_TEXT = 500;

    private InvoiceDraftRules() {}

    /** Only one live (draft or approved) draft may exist for a site and month; a voided one does not count. */
    static void requireNoLiveDraft(List<String> statusesOfThatMonth) {
        for (String s : statusesOfThatMonth) {
            if (!VOID.equals(s)) {
                throw new HandyFlowException("There is already a " + s.toLowerCase(java.util.Locale.ROOT) + " invoice draft for this site and month. Void it first to make a new one.",
                        HttpStatus.CONFLICT, "DRAFT_EXISTS");
            }
        }
    }

    /**
     * Whether a draft may be approved. A month still in progress, or a draft with nothing to bill, is refused outright.
     * Hours with no rate and mixed VAT are real doubts, so the approver must say they have seen them.
     */
    static void requireApprovable(String status, boolean inProgress, long totalCents, double unpricedHours, boolean mixedVat, boolean acknowledged) {
        if (!DRAFT.equals(status)) {
            throw new HandyFlowException("Only a draft can be approved. This one is " + (status == null ? "unknown" : status.toLowerCase(java.util.Locale.ROOT)) + ".",
                    HttpStatus.CONFLICT, "DRAFT_NOT_DRAFT");
        }
        if (inProgress) {
            throw new HandyFlowException("That month is still in progress. Void this draft and make a new one when the month is over.", HttpStatus.CONFLICT, "MONTH_IN_PROGRESS");
        }
        if (totalCents <= 0) {
            throw new HandyFlowException("This draft bills nothing, so there is nothing to approve.", HttpStatus.CONFLICT, "DRAFT_EMPTY");
        }
        if ((unpricedHours > 0 || mixedVat) && !acknowledged) {
            String what = unpricedHours > 0 && mixedVat ? "hours with no contract rate and a mix of VAT-inclusive and VAT-exclusive rates"
                    : unpricedHours > 0 ? "hours with no contract rate" : "a mix of VAT-inclusive and VAT-exclusive rates";
            throw new HandyFlowException("This draft has " + what + ". Confirm you have checked this to approve it.", HttpStatus.CONFLICT, "DRAFT_WARNINGS");
        }
    }

    /** A draft or an approved draft can be voided, with a reason someone can read later. */
    static String requireVoidable(String status, String reason) {
        if (VOID.equals(status)) throw new HandyFlowException("This draft is already void.", HttpStatus.CONFLICT, "DRAFT_ALREADY_VOID");
        if (!DRAFT.equals(status) && !APPROVED.equals(status)) throw new HandyFlowException("Draft not found", HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND");
        return requireText(reason, "A reason for voiding is required (at least " + MIN_REASON + " characters).", "DRAFT_REASON");
    }

    /** A note is optional but bounded; a reason is required. Returns the trimmed text. */
    static String requireText(String text, String message, String code) {
        String t = text == null ? "" : text.strip();
        if (t.length() < MIN_REASON) throw new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
        if (t.length() > MAX_TEXT) throw new HandyFlowException("That is too long (at most " + MAX_TEXT + " characters).", HttpStatus.BAD_REQUEST, code);
        return t;
    }

    static String cleanNote(String note) {
        if (note == null || note.isBlank()) return null;
        String t = note.strip();
        if (t.length() > MAX_TEXT) throw new HandyFlowException("The note is too long (at most " + MAX_TEXT + " characters).", HttpStatus.BAD_REQUEST, "DRAFT_NOTE");
        return t;
    }

    /** The month must look like 2026-10; the billing preview then refuses a future month. */
    static String requireMonth(String month) {
        if (month == null || !month.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new HandyFlowException("Month must look like 2026-10.", HttpStatus.BAD_REQUEST, "DRAFT_MONTH");
        }
        return month;
    }
}
