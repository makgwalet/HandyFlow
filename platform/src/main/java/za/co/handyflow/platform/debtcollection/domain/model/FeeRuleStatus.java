package za.co.handyflow.platform.debtcollection.domain.model;

/** DRAFT → APPROVED (usable) → SUPERSEDED (a newer version was approved) or RETIRED (withdrawn). Only APPROVED rules can be charged. */
public enum FeeRuleStatus { DRAFT, APPROVED, SUPERSEDED, RETIRED }
