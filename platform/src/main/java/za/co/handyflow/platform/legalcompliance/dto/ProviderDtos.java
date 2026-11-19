package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Overview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Provider (consultancy) and client-organisation access. The provider-side responses never include anything from the client's own records; those come only through ClientPosture / report runs. */
public final class ProviderDtos {

    private ProviderDtos() {}

    public record InviteRequest(@NotBlank @Size(max = 255) String providerName, @NotBlank @Size(max = 255) String clientName, @NotEmpty Set<AccessScope> scopes,
                                LocalDate contractStart, LocalDate contractEnd, @NotNull ReviewCycle reviewCycle, String notes) {}

    public record ClientUpdateRequest(@NotBlank @Size(max = 255) String clientName, LocalDate contractStart, LocalDate contractEnd, @NotNull ReviewCycle reviewCycle, String notes) {}

    public record ReissueRequest(@NotEmpty Set<AccessScope> scopes) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record AssignRequest(@NotNull UUID userId, @NotBlank @Size(max = 255) String userName, @NotNull AssignmentRole role) {}

    public record ReviewRequest(@NotNull LocalDate reviewDate, @NotNull ReviewOutcome outcome, @NotBlank String notes) {}

    public record AssignmentResponse(UUID id, UUID userId, String userName, AssignmentRole role, String assignedByName, LocalDate assignedOn, LocalDate endedOn) {
        public static AssignmentResponse of(ProviderAssignment a) {
            return new AssignmentResponse(a.getId(), a.getUserId(), a.getUserName(), a.getRole(), a.getAssignedByName(), a.getAssignedOn(), a.getEndedOn());
        }
    }

    public record ReviewResponse(UUID id, LocalDate reviewDate, String reviewerName, ReviewOutcome outcome, String notes, Map<String, Long> metrics, LocalDate nextReviewDue) {
        public static ReviewResponse of(ProviderReview r) {
            return new ReviewResponse(r.getId(), r.getReviewDate(), r.getReviewerName(), r.getOutcome(), r.getNotes(), r.getMetrics(), r.getNextReviewDue());
        }
    }

    /** inviteExpired and reviewOverdue are worked out on read, never stored. */
    public record ClientResponse(UUID id, String reference, String clientName, String providerName, ProviderClientStatus status, Set<AccessScope> requestedScopes, Set<AccessScope> grantedScopes,
                                 boolean inviteExpired, Instant inviteExpiresAt, Instant acceptedAt, String acceptedByName, AccessSide suspendedBy, String statusReason,
                                 LocalDate contractStart, LocalDate contractEnd, boolean contractEnded, ReviewCycle reviewCycle, LocalDate nextReviewDue, boolean reviewOverdue,
                                 List<AssignmentResponse> team, boolean hasLead, String notes, Instant endedAt, AccessSide endedBy) {}

    public record ClientDetail(ClientResponse client, List<AssignmentResponse> assignmentHistory, List<ReviewResponse> reviews) {}

    /** The code is returned once, here, and never again: only its hash is stored. */
    public record InviteResult(ClientDetail client, String code) {}

    public record ProviderSummary(long active, long invited, long suspended, long reviewsOverdue, long withoutLead, long contractsEnded) {}

    public record WorkloadRow(UUID userId, String userName, long clients, long asLead, List<String> clientNames) {}

    public record ClientPosture(UUID clientId, String clientName, Overview overview) {}

    // ---- client side (the organisation being served) ----

    public record AcceptRequest(@NotBlank String code, @NotEmpty Set<AccessScope> scopes) {}

    /** What the client sees about a provider: who, what is granted, and the state. Contract terms, notes, team and reviews are the provider's own and are not shown. */
    public record ProviderAccessResponse(UUID id, String providerName, ProviderClientStatus status, Set<AccessScope> requestedScopes, Set<AccessScope> grantedScopes,
                                         String acceptedByName, Instant acceptedAt, AccessSide suspendedBy, AccessSide endedBy, Instant endedAt) {}

    /** The invitation as the client sees it before accepting. */
    public record InvitePreview(String providerName, Set<AccessScope> requestedScopes, Instant expiresAt) {}

    public record AccessEvent(Instant at, String eventType, String detail, String actorName) {}
}
