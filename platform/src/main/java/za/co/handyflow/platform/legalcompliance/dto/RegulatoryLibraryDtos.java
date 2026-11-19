package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for the regulatory library, grouped to keep the package tidy. */
public final class RegulatoryLibraryDtos {
    private RegulatoryLibraryDtos() {}

    public record InstrumentRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 100) String identifier,
            @NotNull InstrumentType instrumentType,
            @NotNull JurisdictionLevel jurisdictionLevel,
            @Size(max = 255) String authority,
            @NotNull ObligationCategory topic,
            @Size(max = 500) String sourceUrl,
            String description) {}

    public record InstrumentResponse(
            UUID id, boolean platform, String identifier, String title, InstrumentType instrumentType,
            String jurisdiction, JurisdictionLevel jurisdictionLevel, String authority, ObligationCategory topic,
            String sourceUrl, String description, UUID sourceInstrumentId, boolean reviewed, String reviewedByName,
            Instant reviewedAt, Instant createdAt) {
        public static InstrumentResponse of(RegulatoryInstrument i) {
            return new InstrumentResponse(i.getId(), i.isPlatform(), i.getIdentifier(), i.getTitle(), i.getInstrumentType(),
                    i.getJurisdiction(), i.getJurisdictionLevel(), i.getAuthority(), i.getTopic(), i.getSourceUrl(),
                    i.getDescription(), i.getSourceInstrumentId(), i.isReviewed(), i.getReviewedByName(),
                    i.getReviewedAt(), i.getCreatedAt());
        }
    }

    /**
     * carryForwardFromVersionId: copy every requirement of that version into the new draft (and remember
     * where each came from) so an amendment starts from the previous text instead of a blank page.
     */
    public record VersionRequest(
            @NotBlank @Size(max = 100) String versionLabel,
            LocalDate effectiveDate,
            LocalDate amendedDate,
            @Size(max = 500) String sourceUrl,
            String summary,
            UUID carryForwardFromVersionId) {}

    public record VersionResponse(
            UUID id, UUID instrumentId, String versionLabel, VersionStatus status, LocalDate effectiveDate,
            LocalDate amendedDate, LocalDate repealedDate, String sourceUrl, String summary, Instant publishedAt,
            String publishedByName, long requirementCount) {
        public static VersionResponse of(RegulatoryVersion v, long requirementCount) {
            return new VersionResponse(v.getId(), v.getInstrumentId(), v.getVersionLabel(), v.getStatus(),
                    v.getEffectiveDate(), v.getAmendedDate(), v.getRepealedDate(), v.getSourceUrl(), v.getSummary(),
                    v.getPublishedAt(), v.getPublishedByName(), requirementCount);
        }
    }

    public record InstrumentDetailResponse(InstrumentResponse instrument, List<VersionResponse> versions) {}

    public record RepealRequest(@NotNull LocalDate repealedDate, @NotBlank String reason) {}

    public record RequirementRequest(
            @NotBlank @Size(max = 100) String reference,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(min = 10) String requirementText,
            @NotNull ObligationCategory category,
            RecurrenceInterval recurrence,
            Integer minEmployees,
            List<String> industries,
            List<String> activities,
            String evidenceExpectation) {}

    public record RequirementResponse(
            UUID id, UUID instrumentId, UUID versionId, String reference, String title, String requirementText,
            ObligationCategory category, RecurrenceInterval recurrence, Integer minEmployees, List<String> industries,
            List<String> activities, String evidenceExpectation, UUID previousRequirementId, boolean platform) {
        public static RequirementResponse of(RegulatoryRequirement r) {
            return new RequirementResponse(r.getId(), r.getInstrumentId(), r.getVersionId(), r.getReference(), r.getTitle(),
                    r.getRequirementText(), r.getCategory(), r.getRecurrence(), r.getMinEmployees(), r.getIndustries(),
                    r.getActivities(), r.getEvidenceExpectation(), r.getPreviousRequirementId(), r.getTenantId() == null);
        }
    }

    public record EventResponse(UUID id, String eventType, String detail, String actorName, Instant createdAt) {
        public static EventResponse of(RegulatoryEvent e) {
            return new EventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorName(), e.getCreatedAt());
        }
    }
}
