package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.UUID;

/**
 * The animal or group in the URL is the one a record is for. A body that names a different animal, a group instead, or
 * both is refused, so POST /animals/A/... can never write a record for B. Pure, so it is tested without Spring.
 */
public final class AgPathRules {

    private AgPathRules() {}

    /** For POST /animals/{id}/...: the body must name exactly this animal and no group. */
    public static void requireAnimal(UUID pathAnimalId, UUID bodyAnimalId, UUID bodyGroupId) {
        if (!pathAnimalId.equals(bodyAnimalId) || bodyGroupId != null) {
            throw new IllegalArgumentException("The record must be for the animal in the URL: animalId has to match and groupId has to be empty");
        }
    }

    /** For POST /groups/{id}/...: the body must name exactly this group and no animal. */
    public static void requireGroup(UUID pathGroupId, UUID bodyAnimalId, UUID bodyGroupId) {
        if (!pathGroupId.equals(bodyGroupId) || bodyAnimalId != null) {
            throw new IllegalArgumentException("The record must be for the group in the URL: groupId has to match and animalId has to be empty");
        }
    }
}
