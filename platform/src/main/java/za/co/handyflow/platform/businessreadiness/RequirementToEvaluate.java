package za.co.handyflow.platform.businessreadiness;

import java.util.UUID;

/**
 * One requirement to judge. {@code rule} is null when the requirement has no rule (or is not linked to a tracked requirement). {@code manualStatus} is what the user
 * ticked (MET, MISSING, NOT_APPLICABLE, PENDING_REVIEW); the evaluator never changes it, it only reports where the evidence disagrees.
 */
public record RequirementToEvaluate(UUID requirementId, String label, String manualStatus, RequirementRule rule, boolean newerVersionAvailable) {}
