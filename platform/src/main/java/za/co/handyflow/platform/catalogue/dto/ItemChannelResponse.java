package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One channel as seen from an item: published or not, and what stops it if not. */
public record ItemChannelResponse(UUID channelId, String code, String name, String kind, boolean channelActive, boolean published, Instant publishedAt, List<String> problems) {}
