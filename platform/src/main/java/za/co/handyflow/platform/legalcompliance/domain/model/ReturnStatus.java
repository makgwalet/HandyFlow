package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * PENDING: no answer yet. NIL: nothing to declare. UNCHANGED: earlier declarations still accurate. DECLARED: at least one declaration made through this campaign.
 * LEFT: the person is no longer in the audience (left, or moved out of the department), so they are excused; never set on an answered return.
 */
public enum ReturnStatus { PENDING, NIL, UNCHANGED, DECLARED, LEFT }
