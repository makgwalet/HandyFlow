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

    public record EvidenceView(UUID id, UUID itemId, String fileRef, String note, UUID capturedBy, Instant capturedAt, String status, String supersededReason) {}

    /** Licences linked to one legal entity, by status today. Information only: it does not say what the entity is required to hold. */
    public record CoverageRow(UUID legalEntityId, String name, long valid, long expiring, long expired, List<String> activities) {}

    public record Coverage(List<CoverageRow> entities, long unlinkedLicences) {}
}
