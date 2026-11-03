package za.co.handyflow.platform.projects.domain.enums;

/**
 * COMMITMENT — money promised but not yet incurred (e.g. an approved purchase order).
 * ACTUAL     — money actually incurred (supplier invoice, expense, payroll cost).
 * Amounts are signed: a credit note or a release of a commitment is a NEGATIVE entry.
 */
public enum CostEntryType { COMMITMENT, ACTUAL }