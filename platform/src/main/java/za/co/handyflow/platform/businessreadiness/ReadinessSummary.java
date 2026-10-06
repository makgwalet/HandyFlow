package za.co.handyflow.platform.businessreadiness;

/** Counts by result. Deliberately not a percentage or a score: what needs attention is a list, not a number. */
public record ReadinessSummary(int total, int met, int missing, int expired, int pending, int notEvaluated, int notApplicable, int expiringSoon, int differFromManualStatus) {}
