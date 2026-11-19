package za.co.handyflow.platform.legalcompliance.domain.model;

public enum ImpactOutcome {
    ASSESSED,                    // new requirement has a decision
    REASSESSED,                  // confirmed applicable under the new text, obligation relinked unchanged
    OBLIGATION_UPDATED,          // confirmed applicable and the obligation text/evidence refreshed from the new requirement
    REASSESSED_NOT_APPLICABLE,   // a NOT_APPLICABLE decision now awaits approval
    OBLIGATION_RETIRED,
    OBLIGATION_KEPT_MANUAL,      // obligation kept but detached from the removed requirement
    ACKNOWLEDGED
}
