package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientTenderSnapshotResponse(
        UUID id, UUID clientTenderId, int snapshotNumber, ClientTenderSnapshotData data, Instant submittedAt
) {}
