package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * What can happen to an offer once it exists. Pure. An offer is a draft until the letter goes out, then awaits an answer until it is accepted,
 * declined, withdrawn or lapses. A lapsed offer is worked out from the date, never stored, so nothing has to run to expire it.
 */
public final class RecruiterOfferRules {
    private RecruiterOfferRules() {}

    public static final String DRAFT = "DRAFT", AWAITING = "AWAITING", EXPIRED = "EXPIRED",
            ACCEPTED = "ACCEPTED", DECLINED = "DECLINED", WITHDRAWN = "WITHDRAWN", NONE = "NONE";
    public static final int DEFAULT_VALID_DAYS = 14, MAX_VALID_DAYS = 90, MAX_NOTE = 500;
    private static final Set<String> RESPONSES = Set.of(ACCEPTED, DECLINED, WITHDRAWN);

    /** Where the offer stands today. */
    public static String status(Instant sentAt, String response, LocalDate expiresOn, LocalDate today) {
        if (response != null && !NONE.equals(response)) return response;
        if (sentAt == null) return DRAFT;
        return expiresOn != null && expiresOn.isBefore(today) ? EXPIRED : AWAITING;
    }

    /** The expiry to use when none was given. */
    public static LocalDate expiryOrDefault(LocalDate expiresOn, LocalDate today) {
        return expiresOn != null ? expiresOn : today.plusDays(DEFAULT_VALID_DAYS);
    }

    /** Why this expiry cannot be used, or null. */
    public static String expiryProblem(LocalDate expiresOn, LocalDate today) {
        if (expiresOn == null) return null;
        if (expiresOn.isBefore(today)) return "The offer cannot expire in the past.";
        if (expiresOn.isAfter(today.plusDays(MAX_VALID_DAYS))) return "An offer can stay open for " + MAX_VALID_DAYS + " days at most.";
        return null;
    }

    /** Why the offer letter cannot go out now, or null. A lapsed offer may be sent again with a new date. */
    public static String sendProblem(String status) {
        if (ACCEPTED.equals(status)) return "This offer has already been accepted.";
        if (DECLINED.equals(status) || WITHDRAWN.equals(status)) return "This offer is closed (" + status.toLowerCase() + ").";
        if (AWAITING.equals(status)) return "The offer letter has already been sent and is awaiting an answer.";
        return null;
    }

    /** Why this answer cannot be recorded, or null. */
    public static String responseProblem(String status, String response, String note) {
        String r = response == null ? "" : response.trim().toUpperCase();
        if (!RESPONSES.contains(r)) return "Choose accepted, declined or withdrawn.";
        if (ACCEPTED.equals(status) || DECLINED.equals(status) || WITHDRAWN.equals(status))
            return "This offer has already been " + status.toLowerCase() + ".";
        if (EXPIRED.equals(status) && ACCEPTED.equals(r)) return "This offer has lapsed. Send it again with a new date before recording acceptance.";
        if (!ACCEPTED.equals(r) && (note == null || note.isBlank())) return "Give a reason.";
        if (note != null && note.trim().length() > MAX_NOTE) return "The note is too long (" + MAX_NOTE + " characters at most).";
        return null;
    }

    /** Why the terms cannot be changed now, or null. Once accepted they are the contract. */
    public static String reviseProblem(String status, String reason) {
        if (ACCEPTED.equals(status)) return "An accepted offer cannot be changed.";
        if (DECLINED.equals(status) || WITHDRAWN.equals(status)) return "This offer is closed.";
        if ((AWAITING.equals(status) || EXPIRED.equals(status)) && (reason == null || reason.isBlank()))
            return "Give the reason the terms are changing.";
        if (reason != null && reason.trim().length() > MAX_NOTE) return "The reason is too long (" + MAX_NOTE + " characters at most).";
        return null;
    }

    /** Why the candidate cannot be marked hired on this offer, or null. */
    public static String hireProblem(String status) {
        return ACCEPTED.equals(status) ? null : "Record that the candidate accepted the offer before marking them hired.";
    }
}
