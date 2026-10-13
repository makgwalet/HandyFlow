package za.co.handyflow.platform.debtcollection.domain.model;

/**
 * What an allocation run covers. UNASSIGNED: active cases with no collector. UNAVAILABLE: UNASSIGNED plus cases held by a
 * collector whose profile is inactive or currently away. FROM_USER: every active case held by one named collector
 * (e.g. a leaver), who is excluded as a target.
 */
public enum AllocationScope { UNASSIGNED, UNAVAILABLE, FROM_USER }
