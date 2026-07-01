package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight CP profile returned to the Shield app on startup/login.
 * Uses codename only — never real name — so a glanced-at phone screen
 * can't expose who is being protected (Part 9.3 / Part 9.4).
 *
 * Contains everything the Shield app needs to render its CP home screen:
 *   - Whether the guard has any active assignment
 *   - Their role (TEAM_LEADER, CPO, DRIVER, ADVANCE, COUNTER_SURVEILLANCE)
 *   - The detail ID (for fetching itinerary/team on demand)
 *   - The principal's codename + threat level (NO real name)
 *   - Whether duress is armed (always true when on an active detail)
 */
public record GuardCpProfileResponse(
        boolean     onActiveDetail,
        UUID        detailId,
        String      principalCodename,
        String      principalThreatLevel,
        String      guardRole,
        Instant     detailStartAt,
        Instant     detailEndAt,
        List<ItineraryStopResponse> upcomingStops   // next 3 stops only — full itinerary fetched separately
) {}
