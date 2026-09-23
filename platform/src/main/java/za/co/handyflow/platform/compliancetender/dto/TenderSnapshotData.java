package za.co.handyflow.platform.compliancetender.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The actual frozen shape captured into TenderSubmissionSnapshot.snapshotJson.
 * A plain data record, not a JPA entity — it only ever exists in memory
 * long enough to be serialized once, at submission time.
 */
public record TenderSnapshotData(
        UUID tenderId, String tenderNumber, String name, String tenderAuthority,
        String authorityReferenceNumber, LocalDate closingDate, BigDecimal estimatedValue,
        String industry, String requiredClassOfWork, String status,
        List<RequirementSnapshot> requirements, List<PersonnelSnapshot> personnel, Instant capturedAt
) {
    /** description/source/status copied as they were at capture time — NOT a live reference. */
    public record RequirementSnapshot(String description, String source, String status) {}

    /**
     * employeeFullName/employeeNumber are baked in here, unlike
     * TenderPersonnelResponse's own live-looked-up fields — this is the
     * one place in the whole module where HR data is deliberately copied
     * rather than referenced, because the entire point of a snapshot is
     * that it stays true even if the live HR record changes afterward.
     */
    public record PersonnelSnapshot(UUID employeeId, String role, String employeeFullName, String employeeNumber) {}
}
