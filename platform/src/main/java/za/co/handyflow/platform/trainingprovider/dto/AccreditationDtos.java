package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccreditationDtos {

    private AccreditationDtos() {
    }

    public record AuthorityRequest(@NotBlank String name, @NotBlank String authorityType, String notes) {
    }

    public record RetireRequest(@NotBlank String reason) {
    }

    public record AuthorityView(UUID id, String name, String authorityType, String notes, String status,
                                String retiredReason, Instant retiredAt) {
    }

    public record SiteRequest(@NotBlank String name, @NotBlank String siteType, String address, String province,
                              String contactPerson, String phone) {
    }

    public record SiteView(UUID id, String name, String siteType, String address, String province,
                           String contactPerson, String phone, String status, String retiredReason, Instant retiredAt) {
    }

    public record CreateAccreditationRequest(@NotNull UUID authorityId, @NotBlank String title, String accreditationNumber) {
    }

    public record EditAccreditationRequest(@NotBlank String title, String accreditationNumber, LocalDate expiryDate,
                                           String conditions) {
    }

    /** Moves an accreditation to a new status. Approvals carry the authority's number, dates and where they were verified. */
    public record TransitionRequest(@NotBlank String toStatus, String reason, String accreditationNumber,
                                    LocalDate approvalDate, LocalDate expiryDate, String conditions,
                                    String verificationSource, LocalDate verifiedOn, String overrideReason) {
    }

    public record VerifyRequest(@NotBlank String verificationSource, LocalDate verifiedOn) {
    }

    public record AccreditationView(UUID id, UUID authorityId, String authorityName, String title,
                                    String accreditationNumber, String status, String effectiveStatus,
                                    LocalDate submittedOn, LocalDate approvalDate, LocalDate expiryDate,
                                    Long daysToExpiry, String conditions, String statusReason,
                                    String verificationSource, LocalDate lastVerifiedOn, List<String> nextStatuses,
                                    boolean detailsEditable, int liveScopes) {
    }

    public record EventView(UUID id, String fromStatus, String toStatus, String reason, String overrideReason,
                            UUID actedBy, Instant actedAt) {
    }

    public record ScopeRequest(@NotNull UUID siteId, UUID courseId, @NotBlank String programmeTitle,
                               String qualificationRef, Integer nqfLevel, Integer credits, LocalDate validFrom,
                               LocalDate validTo, String notes) {
    }

    public record ScopeView(UUID id, UUID accreditationId, UUID siteId, String siteName, UUID courseId,
                            String programmeTitle, String qualificationRef, Integer nqfLevel, Integer credits,
                            LocalDate validFrom, LocalDate validTo, String notes, String status,
                            String withdrawnReason, Instant withdrawnAt) {
    }

    public record AccreditationDetail(AccreditationView accreditation, List<ScopeView> scopes, List<EventView> history) {
    }

    public record CoverageView(UUID courseId, UUID siteId, LocalDate on, String outcome, boolean covered, String message) {
    }
}
