package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * What the reviewer decides.
 * CHANGED: CONFIRM_APPLICABLE | NO_LONGER_APPLICABLE.
 * REMOVED: RETIRE_OBLIGATION | KEEP_OBLIGATION | ACKNOWLEDGE (only when no live obligation exists).
 * ADDED: ACKNOWLEDGE (only once the new requirement has been assessed).
 */
public enum ImpactAction { CONFIRM_APPLICABLE, NO_LONGER_APPLICABLE, RETIRE_OBLIGATION, KEEP_OBLIGATION, ACKNOWLEDGE }
