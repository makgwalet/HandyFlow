package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

/** One attribute value on an item. */
public record CatalogueAttributeValueResponse(UUID definitionId, String code, String label, String type, String value) {}
