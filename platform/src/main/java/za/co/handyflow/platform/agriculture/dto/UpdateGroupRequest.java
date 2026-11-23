package za.co.handyflow.platform.agriculture.dto;

import java.util.List;
import java.util.UUID;

/** A field left out (null) keeps its value. To empty one, list its name in {@code clear} (productionAreaId, enterpriseId, breed, notes). */
public record UpdateGroupRequest(UUID productionAreaId, UUID enterpriseId, String breed, String notes, List<String> clear) {}
