package za.co.handyflow.platform.debtcollection.domain.model;

/**
 * Which legal route applies is a classification a person must make — the system never guesses.
 * NCA_CREDIT_AGREEMENT agreements need a reviewed section 129 notice before handover; UNKNOWN blocks submission.
 */
public enum CreditAgreementType {
    NCA_CREDIT_AGREEMENT, OTHER_COMMERCIAL, UNKNOWN
}
