package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

/** itemCount is how many live items use the unit; null where it was not worked out. */
public record CatalogueUnitResponse(UUID id, String code, String name, String kind, int decimals, boolean active, Long itemCount) {}
