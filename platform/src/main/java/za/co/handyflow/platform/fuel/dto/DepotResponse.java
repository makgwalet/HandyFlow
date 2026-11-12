package za.co.handyflow.platform.fuel.dto;

import java.time.Instant;
import java.util.UUID;

public record DepotResponse(UUID id, String name, String code, String address, boolean active, int tankCount, Instant createdAt, UUID supplyingDepotId) {
    public DepotResponse(UUID id, String name, String code, String address, boolean active, int tankCount, Instant createdAt) { this(id, name, code, address, active, tankCount, createdAt, null); }
}
