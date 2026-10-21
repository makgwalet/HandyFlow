package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

public record CataloguePriceListResponse(UUID id, String code, String name, boolean defaultList, boolean active, int sortOrder) {}
