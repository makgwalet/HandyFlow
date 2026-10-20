package za.co.handyflow.platform.fleet.dto;

import java.util.List;

/**
 * What was done with an ingest batch. One bad item never fails the batch: it is listed in
 * {@code rejected} (capped at 50) and the rest are processed. Duplicate positions (same vehicle and
 * time) are ignored silently and still count in {@code positionsAccepted}.
 */
public record TelematicsIngestResponse(
        int positionsAccepted,
        int eventsAccepted,
        int eventsDuplicate,
        int derivedEvents,            // speeding / idling / geofence events our rules raised from the positions
        int rejectedCount,
        List<Rejection> rejected,
        boolean rejectionsTruncated
) {
    public record Rejection(String kind, int index, String reason) {}
}