package za.co.handyflow.platform.complianceservices.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The frozen shape captured into ClientTenderSubmissionSnapshot.snapshotJson.
 * Now includes a personnel section — added once the personnel-reference
 * design question was resolved (see ClientTenderPersonnel's own
 * Javadoc), following that decision rather than anticipating it.
 */
public record ClientTenderSnapshotData(
        UUID clientTenderId, UUID clientId, String tenderNumber, String name, String tenderAuthority,
        String authorityReferenceNumber, LocalDate closingDate, BigDecimal estimatedValue,
        String industry, String requiredClassOfWork, String status,
        List<RequirementSnapshot> requirements, List<PersonnelSnapshot> personnel, Instant capturedAt
) {
    public record RequirementSnapshot(String description, String source, String status) {}

    /**
     * employeeFullName/employeeNumber are baked in here, unlike
     * ClientTenderPersonnelResponse's own live-looked-up fields — the
     * one deliberate exception to reference-not-copy in this part of the
     * module, same reasoning as compliancetender.TenderSnapshotData's
     * own PersonnelSnapshot: the whole point of a snapshot is that it
     * stays true even after the live HR record changes.
     */
    public record PersonnelSnapshot(UUID employeeId, String role, String employeeFullName, String employeeNumber) {}
}
