package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.UUID;

/**
 * Where a notification takes the person. Every URL here is a page the web app really has: /agriculture/farms opens a
 * farm (and from there an animal, group or tab) from its query, /agriculture/crop-cycles picks the farm from ?farm=.
 * The web side reads the same names (agDeepLink.logic.ts); change them together.
 */
public final class AgActionUrls {

    private static final String FARMS = "/agriculture/farms?farm=";

    private AgActionUrls() {}

    public static String animal(UUID farmId, UUID animalId) { return FARMS + farmId + "&animal=" + animalId; }

    public static String group(UUID farmId, UUID groupId) { return FARMS + farmId + "&group=" + groupId; }

    /** The animal if the record is for one, otherwise the group, otherwise just the farm. */
    public static String animalOrGroup(UUID farmId, UUID animalId, UUID groupId) {
        if (animalId != null) return animal(farmId, animalId);
        if (groupId != null) return group(farmId, groupId);
        return FARMS + farmId;
    }

    public static String inventory(UUID farmId) { return FARMS + farmId + "&tab=inventory"; }

    public static String cropCycles(UUID farmId) { return "/agriculture/crop-cycles?farm=" + farmId; }
}
