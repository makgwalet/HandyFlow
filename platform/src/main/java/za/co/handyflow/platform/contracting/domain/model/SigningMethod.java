package za.co.handyflow.platform.contracting.domain.model;

/**
 * How a contract is signed. Only SIMPLE_ELECTRONIC (OTP plus drawn signature) is supported today. The others exist so a
 * signing provider can be added later without reworking Contracting. Which documents need which method is a legal
 * decision recorded as data by the legal reviewer; nothing here assumes it.
 */
public enum SigningMethod {
    SIMPLE_ELECTRONIC,
    ADVANCED_ELECTRONIC,
    WET_INK,
    IN_PERSON
}
