package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;

import java.time.Instant;

/**
 * The SLA a client may see. Only the resolution (fix-by) target is shown: "response" is an internal dispatch
 * measure. Nothing is shown until staff approve the request (the target is stamped at submission but only
 * starts to mean something once the work is accepted), nor for declined or withdrawn requests.
 */
public final class FmPortalSla {

    private FmPortalSla() {}

    private static boolean visible(FmWorkOrder w) {
        return !"REQUESTED".equals(w.getStatus()) && !"REJECTED".equals(w.getStatus()) && !"CANCELLED".equals(w.getStatus());
    }

    public static Instant dueAt(FmWorkOrder w) {
        return visible(w) ? w.getResolutionDueAt() : null;
    }

    /** ON_TRACK, AT_RISK, PAUSED, MET or BREACHED; null when there is nothing to show. */
    public static String state(FmWorkOrder w, Instant now) {
        if (!visible(w) || w.getResolutionDueAt() == null) return null;
        String s = FmSlaStatus.resolution(w, now);
        return "NONE".equals(s) ? null : s;
    }
}
