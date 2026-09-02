package za.co.handyflow.platform.debtcollection.domain.model;

/** Result of a single contact attempt, recorded on CollectionContactLog. */
public enum ContactOutcome {
    NO_ANSWER,
    LEFT_MESSAGE,
    PROMISE_TO_PAY,
    DISPUTED,
    REFUSED_TO_PAY,
    ALREADY_PAID,
    WRONG_CONTACT_DETAILS,
    OTHER
}
