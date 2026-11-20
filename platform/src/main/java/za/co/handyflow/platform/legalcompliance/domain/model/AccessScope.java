package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * What a provider may do in a client's workspace. SUMMARY = see posture counts, indicators and the attention list; REPORTS = run (never export) the standard reports;
 * SUBMIT = send the client observations, recommendations and requests for information, which the client's staff accept, decline or complete. SUBMIT never changes any of the
 * client's registers: it writes only to the client's inbox of provider submissions.
 */
public enum AccessScope { SUMMARY, REPORTS, SUBMIT }
