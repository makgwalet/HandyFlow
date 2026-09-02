package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * PROPOSED lifecycle — the strategic plan lists the fields a litigation
 * matter needs (opposing party, matter type, status, estimated exposure,
 * legal representative, key dates, linked documents) but does not
 * prescribe the exact status vocabulary the way it does for e.g. Debt
 * Collection's REMINDER→DEMAND→FINAL_DEMAND stages. This is a reasonable
 * default modelled on how a matter actually progresses, not a confirmed
 * requirement — flagging it explicitly as one of this module's open
 * design points rather than presenting it as settled.
 */
public enum LitigationStatus {
    OPEN,
    IN_PROGRESS,
    SETTLED,
    WITHDRAWN,
    CLOSED
}
