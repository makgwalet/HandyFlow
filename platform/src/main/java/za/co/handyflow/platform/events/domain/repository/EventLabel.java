package za.co.handyflow.platform.events.domain.repository;

import java.util.UUID;

/** Interface projection pairing an event id with one label (e.g. its budget status). */
public interface EventLabel {
    UUID getEventId();
    String getLabel();
}