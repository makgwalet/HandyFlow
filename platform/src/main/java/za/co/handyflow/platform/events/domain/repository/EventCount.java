package za.co.handyflow.platform.events.domain.repository;

import java.util.UUID;

/** Interface projection for "SELECT eventId AS eventId, COUNT(...) AS total ... GROUP BY eventId" queries. */
public interface EventCount {
    UUID getEventId();
    Long getTotal();
}