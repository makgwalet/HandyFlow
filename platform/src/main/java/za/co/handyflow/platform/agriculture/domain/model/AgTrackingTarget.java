package za.co.handyflow.platform.agriculture.domain.model;

import java.util.UUID;

/**
 * Centralizes the one invariant every Livestock history entity shares:
 * a record belongs to EITHER an individually-tracked {@link AgAnimal} OR
 * a {@link AgGroup} batch/flock/herd — never both, never neither. Kept as
 * a single static check (rather than repeating the same four lines in six
 * entities' {@code create()} methods) so the rule can only be expressed
 * one way across the whole module — see {@code package-info.java}'s
 * "INDIVIDUAL VS. GROUP TRACKING" section for the reasoning behind the
 * rule itself.
 * <p>
 * Not an {@code @Embeddable} — it deliberately holds no state and is
 * never persisted; each entity keeps its own nullable {@code animalId}/
 * {@code groupId} columns so repository queries can filter on either
 * directly without unwrapping a value object.
 */
final class AgTrackingTarget {

    private AgTrackingTarget() {
    }

    static void requireExactlyOne(UUID animalId, UUID groupId) {
        boolean hasAnimal = animalId != null;
        boolean hasGroup = groupId != null;
        if (hasAnimal == hasGroup) {
            throw new IllegalArgumentException(
                    "exactly one of animalId or groupId is required (got animalId=" + animalId + ", groupId=" + groupId + ")");
        }
    }
}
