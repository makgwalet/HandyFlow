package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.List;

/**
 * Work orders the caller can see that changed at or after {@code since}. Ask again with {@code nextSince} while
 * {@code more} is true; items are inclusive of the boundary instant, so upsert by id.
 */
public record WorkOrderChangesResponse(List<WorkOrderResponse> items, Instant nextSince, boolean more) {}
