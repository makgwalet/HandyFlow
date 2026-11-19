package za.co.handyflow.platform.contracting.application.internal;

import java.util.UUID;

/** A counterparty proposed or commented on a negotiation. Published inside the transaction, delivered to staff after it commits. */
record NegotiationActivity(UUID tenantId, UUID contractId, String kind, String partyName) {
    static final String PROPOSAL = "PROPOSAL", COMMENT = "COMMENT";
}
