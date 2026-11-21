package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/** Retry schedule for webhook deliveries: 5 attempts in total, backing off 1/5/15/30/60 minutes. */
final class WebhookRetryRules {

    static final int MAX_ATTEMPTS = 5;
    private static final int[] DELAY_MINUTES = {1, 5, 15, 30, 60};

    private WebhookRetryRules() {}

    /** When to try again after {@code attempt} failed, or null when no attempts are left. */
    static Instant nextRetryAt(int attempt, Instant now) {
        if (attempt >= MAX_ATTEMPTS) return null;
        int idx = Math.max(0, Math.min(attempt - 1, DELAY_MINUTES.length - 1));
        return now.plus(Duration.ofMinutes(DELAY_MINUTES[idx]));
    }

    /** A delivery whose payload was not stored (rows from before V847) cannot be replayed faithfully. */
    static boolean canReplay(String storedPayload) {
        return storedPayload != null && !storedPayload.isBlank();
    }
}
