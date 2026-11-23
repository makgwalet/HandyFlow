package za.co.handyflow.platform.agriculture.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Breeds (per species) and feed types already in use on a farm, to offer as suggestions. Free text is still allowed. */
public record SuggestionsResponse(Map<UUID, List<String>> breedsBySpecies, List<String> feedTypes) {}
