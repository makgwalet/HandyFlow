package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

/** An item published to a channel, whatever its status now. {@code name} is empty when the item has since been deleted. */
public record ChannelItemResponse(UUID itemId, String name, String sku, String status, Instant publishedAt) {}
