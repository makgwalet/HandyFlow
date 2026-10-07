package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public final class FillDtos {
    private FillDtos() {}

    public record FillRequest(Integer quantity, String note) {}

    public record FillResponse(UUID id, UUID prescriptionId, int fillNumber, Integer quantity,
                               UUID dispensedBy, String note, Instant createdAt) {}
}
