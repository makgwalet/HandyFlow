package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** F01: licence evidence and per-entity coverage. */
public final class ComplianceLinkageDtos {
    private ComplianceLinkageDtos() {}

    public record EvidenceRequest(@NotBlank @Size(max = 500) String fileRef, @Size(max = 300) String note) {}

    /** fileRef is the link or reference typed in, or null for a stored file (which is read through its own download, never by its store key). */
    public record EvidenceView(UUID id, UUID itemId, String fileRef, String note, UUID capturedBy, Instant capturedAt, String status, String supersededReason,
                               boolean fileStored, String contentType, Integer fileSizeBytes, String fileName, UUID verifiedBy, Instant verifiedAt) {}

    /** Licences linked to one legal entity, by status today. Information only: it does not say what the entity is required to hold. */
    public record CoverageRow(UUID legalEntityId, String name, long valid, long expiring, long expired, List<String> activities) {}

    /** Licences linked to one depot, by status today. */
    public record SiteCoverageRow(UUID depotId, String name, UUID legalEntityId, String legalEntityName, long valid, long expiring, long expired, List<String> activities) {}

    public record Coverage(List<CoverageRow> entities, long unlinkedLicences, List<SiteCoverageRow> sites) {
        public Coverage(List<CoverageRow> entities, long unlinkedLicences) { this(entities, unlinkedLicences, List.of()); }
    }
}
