package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LearnerMatchDtos {

    private LearnerMatchDtos() {
    }

    /** What is typed so far on a new learner, to look for someone already held. */
    public record MatchRequest(String firstNames, String surname, String idNumber, LocalDate dateOfBirth, String email, String phone) {
    }

    /** One record that may be the same person. kind is LEARNER or DELEGATE. */
    public record PersonView(String kind, UUID id, String number, String name, String idNumberMasked, LocalDate dateOfBirth, String email,
                             String phone, String status, UUID linkedLearnerId) {
    }

    public record MatchView(PersonView person, String strength, List<String> reasons, List<String> conflicts) {
    }

    public record PairView(PersonView first, PersonView second, String strength, List<String> reasons, List<String> conflicts) {
    }

    public record UnmatchedDelegateView(PersonView delegate, UUID clientId, String clientName, List<MatchView> candidates) {
    }

    public record LearnerEventView(UUID id, String eventType, UUID delegateId, UUID otherLearnerId, String reason, UUID actedBy, Instant actedAt) {
    }

    public record MergeProposalRequest(@NotNull UUID survivorId, @NotNull UUID mergedId, @NotBlank String reason, boolean conflictsAccepted) {
    }

    public record MergeDecisionRequest(@NotBlank String reason, boolean confirmAlone, String singleReason) {
    }

    public record MergeView(UUID id, String status, UUID survivorId, String survivorName, String survivorNo, UUID mergedId, String mergedName,
                            String mergedNo, List<String> matchReasons, List<String> conflicts, boolean conflictsAccepted, String reason,
                            String survivorSnapshot, String mergedSnapshot, UUID proposedBy, Instant proposedAt, UUID decidedBy,
                            Instant decidedAt, String decisionReason, Integer movedEnrolments, Integer movedApplications,
                            Integer movedDocuments, String delegateNote) {
    }
}
