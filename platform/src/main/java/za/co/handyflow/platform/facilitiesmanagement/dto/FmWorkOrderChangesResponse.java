package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.List;

/**
 * Work orders the caller can see that changed at or after {@code since}. Ask again with {@code nextSince} while
 * {@code more} is true; items are inclusive of the boundary instant, so upsert by id.
 */
public record FmWorkOrderChangesResponse(List<FmWorkOrderResponse> items, Instant nextSince, boolean more) {}
