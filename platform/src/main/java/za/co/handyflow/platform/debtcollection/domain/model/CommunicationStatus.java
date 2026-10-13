package za.co.handyflow.platform.debtcollection.domain.model;

/**
 * Outbound: QUEUED → SENT → DELIVERED (forward-only), or FAILED / BOUNCED (before delivery), RETURNED (letters),
 * BLOCKED (refused by CommunicationPolicy — recorded, never silently dropped). Inbound: RECEIVED.
 */
public enum CommunicationStatus {
    QUEUED, SENT, DELIVERED, FAILED, BOUNCED, RETURNED, BLOCKED, RECEIVED
}
