package za.co.handyflow.platform.complianceservices.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The frozen shape captured into ClientTenderSubmissionSnapshot.snapshotJson.
 * Deliberately no personnel section yet — see ClientTenderSubmissionSnapshot's
 * own Javadoc for why that follows the open personnel-reference design
 * question, not anticipates it.
 */
public record ClientTenderSnapshotData(
        UUID clientTenderId, UUID clientId, String tenderNumber, String name, String tenderAuthority,
        String authorityReferenceNumber, LocalDate closingDate, BigDecimal estimatedValue,
        String industry, String requiredClassOfWork, String status,
        List<RequirementSnapshot> requirements, Instant capturedAt
) {
    public record RequirementSnapshot(String description, String source, String status) {}
}
