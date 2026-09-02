package za.co.handyflow.platform.debtcollection.domain.model;

/** How a debtor was contacted for a given CollectionContactLog entry. */
public enum ContactMethod {
    PHONE_CALL,
    EMAIL,
    SMS,
    WHATSAPP,
    LETTER,
    IN_PERSON,
    OTHER
}
