package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * DRAFT is editable. Publishing makes a version CURRENT (and the previous CURRENT SUPERSEDED);
 * from then on its requirements are immutable — an amendment is a NEW version, never an edit,
 * so historical assessments always point at the text that existed when they were made.
 */
public enum VersionStatus { DRAFT, CURRENT, SUPERSEDED, REPEALED }
