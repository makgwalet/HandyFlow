package za.co.handyflow.platform.debtcollection.domain.model;

/** Message channels recorded on the case timeline. Phone calls and in-person contact stay in CollectionContactLog. */
public enum CommunicationChannel {
    SMS,
    EMAIL,
    /** Physical/registered letters — dispatch is recorded by staff, not sent by the system. */
    LETTER,
    /** OFF by default: Meta's Business Messaging Policy lists debt collection among prohibited uses. See CommunicationPolicy. */
    WHATSAPP
}
