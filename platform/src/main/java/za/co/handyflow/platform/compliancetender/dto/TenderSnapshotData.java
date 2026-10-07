package za.co.handyflow.platform.compliancetender.dto;

import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code pricing} is the price schedule exactly as it stood at submission: null for a tender never priced, and for snapshots taken before pricing existed.
 * {@code readiness} is the evidence check as it stood at submission, judged against the closing date: null for snapshots taken before it was stored.
 * The actual frozen shape captured into TenderSubmissionSnapshot.snapshotJson.
 * A plain data record, not a JPA entity — it only ever exists in memory
 * long enough to be serialized once, at submission time.
 */
public record TenderSnapshotData(
        UUID tenderId, String tenderNumber, String name, String tenderAuthority,
        String authorityReferenceNumber, LocalDate closingDate, BigDecimal estimatedValue,
        String industry, String requiredClassOfWork, String status,
        List<RequirementSnapshot> requirements, List<PersonnelSnapshot> personnel, Instant capturedAt,
        TenderPricingResponse pricing,
        PackageReference submittedPackage,
        ReadinessAssessment readiness
) {
    /** The same record with the price schedule left out, for people who may read tenders but not their pricing. */
    public TenderSnapshotData withoutPricing() {
        return new TenderSnapshotData(tenderId, tenderNumber, name, tenderAuthority, authorityReferenceNumber, closingDate, estimatedValue, industry, requiredClassOfWork,
                status, requirements, personnel, capturedAt, null, submittedPackage, readiness);
    }

    /**
     * The package that was current when the tender was submitted: which version, what it was called, its hash, and whether the tender had
     * changed since it was built. Null when no package had been built, and for snapshots taken before packages were linked.
     */
    public record PackageReference(UUID packageId, int versionNo, String fileName, String packageHash, boolean submissionReady,
                                   Instant builtAt, boolean outOfDate, List<String> outOfDateReasons) {}

    /** description/source/status copied as they were at capture time — NOT a live reference. */
    public record RequirementSnapshot(String description, String source, String status) {}

    /**
     * employeeFullName/employeeNumber are baked in here, unlike
     * TenderPersonnelResponse's own live-looked-up fields — this is the
     * one place in the whole module where HR data is deliberately copied
     * rather than referenced, because the entire point of a snapshot is
     * that it stays true even if the live HR record changes afterward.
     */
    public record PersonnelSnapshot(UUID employeeId, String role, String employeeFullName, String employeeNumber,
                                    String personType, String externalOrganisation) {}
}
