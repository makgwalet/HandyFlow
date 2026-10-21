package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

public record ChannelResponse(UUID id, String code, String name, String kind, UUID priceListId, String priceListName, boolean active, long publishedCount) {}
