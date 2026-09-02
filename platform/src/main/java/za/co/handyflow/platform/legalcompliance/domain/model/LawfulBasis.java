package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * POPIA Section 11(1) lawful grounds for processing personal information.
 * CONSENT and CONTRACT are confirmed directly against
 * crm.domain.model.CustomerConsent.LawfulBasis (same names, kept
 * identical deliberately — this register is meant to speak the same
 * vocabulary CRM already uses for customer data, not a competing one).
 * The remaining four values are the rest of POPIA s11(1)(c)-(f) as a
 * statutory list, not independently re-confirmed against
 * CustomerConsent's full enum this session — worth a direct diff against
 * that file before this ships, flagged rather than silently assumed
 * identical.
 */
public enum LawfulBasis {
    CONSENT,
    CONTRACT,
    LEGAL_OBLIGATION,
    PROTECT_VITAL_INTEREST,
    PUBLIC_LAW_DUTY,
    LEGITIMATE_INTEREST
}
