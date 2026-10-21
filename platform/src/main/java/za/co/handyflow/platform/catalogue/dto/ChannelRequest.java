package za.co.handyflow.platform.catalogue.dto;

import java.util.UUID;

/** {@code priceListId} empty means the default list. {@code active} left out means active. The code cannot be changed later. */
public record ChannelRequest(String code, String name, String kind, UUID priceListId, Boolean active) {}
