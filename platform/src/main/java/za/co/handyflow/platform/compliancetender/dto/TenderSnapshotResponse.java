package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.util.UUID;

public record TenderSnapshotResponse(
        UUID id, UUID tenderId, int snapshotNumber, TenderSnapshotData data, Instant submittedAt
) {}
