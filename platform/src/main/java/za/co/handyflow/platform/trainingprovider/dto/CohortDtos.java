package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CohortDtos {
    private CohortDtos() {
    }

    public record VenueRequest(@NotNull UUID siteId, @NotBlank String name, Integer capacity, String notes) {
    }

    public record VenueView(UUID id, UUID siteId, String siteName, String name, Integer capacity, String notes, String status, String inactiveReason) {
    }

    public record Reason(@NotBlank String reason) {
    }

    public record CohortRequest(@NotBlank String name, @NotNull UUID programmeId, @NotNull UUID siteId, @NotNull LocalDate startDate,
                                LocalDate endDate, Integer capacity, String notes) {
    }

    public record CohortUpdate(@NotBlank String name, @NotNull LocalDate startDate, LocalDate endDate, Integer capacity, String notes) {
    }

    public record CohortTransition(@NotBlank String toStatus, String reason) {
    }

    public record AddMember(@NotNull UUID enrolmentId) {
    }

    public record AssignFacilitator(@NotNull UUID practitionerId) {
    }

    public record CohortView(UUID id, String cohortNo, String name, UUID programmeId, String programmeTitle, Integer programmeVersionNo, UUID siteId,
                             String siteName, LocalDate startDate, LocalDate endDate, Integer capacity, String notes, String status, String statusReason,
                             long members, long facilitators, List<String> nextStatuses) {
    }

    public record MemberView(UUID id, UUID enrolmentId, String enrolmentNo, UUID learnerId, String learnerName, String enrolmentStatus, String status,
                             String removedReason, Instant addedAt) {
    }

    public record FacilitatorView(UUID id, UUID practitionerId, String practitionerNo, String name, UUID appointmentId, String standing,
                                  String standingNote, String status, String removedReason, String sitePermission, String sitePermissionNote) {
    }

    public record EventView(UUID id, String action, String note, UUID actedBy, Instant actedAt) {
    }

    public record CohortDetail(CohortView cohort, List<MemberView> members, List<FacilitatorView> facilitators, List<EventView> history) {
    }
}
