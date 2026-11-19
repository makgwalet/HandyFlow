package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * APPLICABLE decisions are APPROVED immediately (they only add duties).
 * NOT_APPLICABLE decisions wait in PENDING_APPROVAL — excluding a legal duty is the risky direction.
 * A newer approved decision SUPERSEDES the old one; rows are never edited or deleted.
 */
public enum AssessmentStatus { PENDING_APPROVAL, APPROVED, REJECTED, SUPERSEDED }
