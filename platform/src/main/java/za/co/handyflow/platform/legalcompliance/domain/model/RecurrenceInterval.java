package za.co.handyflow.platform.legalcompliance.domain.model;

/** How often a RegulatoryObligation's review date rolls forward once marked reviewed. ONCE = a one-time obligation that does not recur. */
public enum RecurrenceInterval {
    ONCE,
    MONTHLY,
    QUARTERLY,
    ANNUALLY
}
