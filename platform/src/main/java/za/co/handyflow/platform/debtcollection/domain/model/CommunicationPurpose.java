package za.co.handyflow.platform.debtcollection.domain.model;

/** Why a message is being sent. REMINDER and DEMAND are "collection" purposes and are subject to the strictest policy rules. */
public enum CommunicationPurpose {
    REMINDER,
    DEMAND,
    PTP_CONFIRMATION,
    PAYMENT_ACKNOWLEDGEMENT,
    DISPUTE_RESPONSE,
    OTHER;

    public boolean isCollection() {
        return this == REMINDER || this == DEMAND;
    }
}
