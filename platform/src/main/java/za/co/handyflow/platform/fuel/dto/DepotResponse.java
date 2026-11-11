package za.co.handyflow.platform.fuel.dto;

import java.time.Instant;
import java.util.UUID;

public record DepotResponse(UUID id, String name, String code, String address, boolean active, int tankCount, Instant createdAt) {}
