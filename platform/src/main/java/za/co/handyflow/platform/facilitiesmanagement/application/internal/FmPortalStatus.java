package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import java.util.List;

/**
 * How internal work-order states are presented to a client. Internal detail stays internal:
 * PENDING_VERIFICATION is shown as IN_PROGRESS, a request staff have not yet approved is SUBMITTED, and a
 * request staff turned down is DECLINED (without the staff's reason).
 */
public final class FmPortalStatus {

    /** Statuses a client can ever be shown (REQUESTED/REJECTED only for requests the client raised — enforced by the query). */
    public static final List<String> LISTABLE = List.of(
            "REQUESTED", "REJECTED", "OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION", "COMPLETED", "CANCELLED");

    private FmPortalStatus() {}

    public static String present(String internal) {
        if (internal == null) return null;
        return switch (internal) {
            case "PENDING_VERIFICATION" -> "IN_PROGRESS";
            case "REQUESTED" -> "SUBMITTED";
            case "REJECTED" -> "DECLINED";
            default -> internal;
        };
    }

    /** Client-facing filter → the internal statuses it covers. Unknown filters match nothing. */
    public static List<String> filter(String clientStatus) {
        if (clientStatus == null || clientStatus.isBlank()) return LISTABLE;
        String s = clientStatus.trim().toUpperCase();
        return switch (s) {
            case "IN_PROGRESS" -> List.of("IN_PROGRESS", "PENDING_VERIFICATION");
            case "SUBMITTED" -> List.of("REQUESTED");
            case "DECLINED" -> List.of("REJECTED");
            case "OPEN", "ASSIGNED", "ON_HOLD", "COMPLETED", "CANCELLED" -> List.of(s);
            default -> List.of("NONE");
        };
    }
}
