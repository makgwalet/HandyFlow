package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.util.UUID;

public record TenderSnapshotResponse(
        UUID id, UUID tenderId, int snapshotNumber, TenderSnapshotData data, Instant submittedAt
) {
    public TenderSnapshotResponse withoutPricing() { return new TenderSnapshotResponse(id, tenderId, snapshotNumber, data.withoutPricing(), submittedAt); }
}
