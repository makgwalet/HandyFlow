package za.co.handyflow.platform.security.application.internal;

import java.util.UUID;

/**
 * Shared-device handover: when the next guard signs in on a shared device, the previous guard's open device session
 * is closed. Before this it stayed open, and the next guard could not start their own session on the device
 * ("A session is already open on this device") until a supervisor force-closed it.
 */
final class HandoverRules {

    static final String REASON = "Shared device handover: next guard signed in";

    private HandoverRules() {}

    /** Only another guard's session is closed; a guard signing in again on their own device keeps their session. */
    static boolean shouldClose(UUID openSessionGuardId, UUID incomingGuardId) {
        return openSessionGuardId != null && incomingGuardId != null && !openSessionGuardId.equals(incomingGuardId);
    }
}
