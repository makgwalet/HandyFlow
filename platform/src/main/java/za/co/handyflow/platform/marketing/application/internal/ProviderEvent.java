package za.co.handyflow.platform.marketing.application.internal;

import java.time.Instant;
import java.util.UUID;

/** Provider-neutral event. type: DELIVERED | HARD_BOUNCE | COMPLAINT. eventId must be stable across provider retries. */
public record ProviderEvent(String provider, String eventId, String type, UUID campaignContactId,
                            String email, Instant occurredAt, String reason) {}