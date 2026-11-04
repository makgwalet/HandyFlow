package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requests and responses for the Projects client portal (named client logins, patch 0573) and its staff side.
 * What a client sees is deliberately narrow: no budget, spend, hours, rates, assignees or internal notes. Change orders
 * show only their value and time effect, and only once approved internally.
 */
public final class ClientPortalDtos {
    private ClientPortalDtos() {}

    // ── Auth ──────────────────────────────────────────────────────────────────
    public record RegisterRequest(@NotBlank String inviteToken, @NotBlank @Size(min = 8, max = 200) String password,
                                  @Size(max = 255) String fullName) {}
    public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 200) String password) {}
    public record AuthResponse(String token, UUID portalUserId, String email, String fullName) {}
    /** What the accept-invite page shows before the person chooses a password. existingAccount: enter the current password instead. */
    public record InviteInfo(String clientName, String email, String contactName, String invitedByName, boolean existingAccount,
                             Instant expiresAt) {}

    // ── Client side ───────────────────────────────────────────────────────────
    public record Account(UUID grantId, String clientName) {}
    public record Me(String fullName, String email, List<Account> accounts) {}

    public record MyProject(UUID id, String projectNumber, String name, String clientName, String status, String health,
                            LocalDate startDate, LocalDate endDate, BigDecimal completionPct, int openSnags,
                            int changeOrdersAwaiting, int openRequests, int answeredRequests) {}

    /** clientState: AWAITING (needs the client's decision), APPROVED, DISPUTED (open dispute). */
    public record ClientChangeOrder(UUID id, String changeNumber, String title, String description, String reason,
                                    BigDecimal costImpact, int scheduleImpactDays, Instant approvedAt, String clientState,
                                    Instant clientApprovedAt, Instant disputedAt, String disputeReason,
                                    Instant disputeResolvedAt, String disputeResolution) {}

    public record RequestView(UUID id, String requestNumber, String kind, UUID projectId, String title, String description,
                              LocalDate neededBy, String status, String response, String raisedByName, String raisedByEmail,
                              String decidedByName, Instant decidedAt, String linkedType, UUID linkedId, Instant createdAt) {}

    public record UploadView(UUID id, UUID projectId, String fileName, long sizeBytes, String note, String status, String reason,
                             UUID documentId, String uploadedByName, String uploadedByEmail, String decidedByName,
                             Instant decidedAt, Instant createdAt) {}

    public record ProjectView(MyProject project, String siteAddress, List<PortalMilestoneResponse> milestones,
                              List<PortalSnagResponse> openSnags, List<PortalRiskResponse> redRisks,
                              List<PortalDocumentResponse> documents, List<ClientChangeOrder> changeOrders,
                              List<RequestView> requests, List<UploadView> uploads) {}

    public record RaiseRequest(@NotBlank @Pattern(regexp = "QUESTION|CHANGE|NEW_PROJECT", message = "Unknown request type") String kind,
                               UUID projectId, UUID grantId,
                               @NotBlank(message = "Give the request a title") @Size(max = 300) String title,
                               @Size(max = 5000) String description, LocalDate neededBy) {}

    public record DisputeRequest(@NotBlank(message = "Say what you dispute") @Size(max = 1000) String reason) {}

    // ── Staff side: access ────────────────────────────────────────────────────
    public record ProjectOption(UUID id, String projectNumber, String name, String status) {}
    public record ClientOption(UUID clientId, String clientName, List<ProjectOption> projects) {}

    public record GrantView(UUID id, UUID clientId, String clientName, boolean allProjects, List<ProjectOption> projects,
                            String email, String contactName, String status, String invitedByName, Instant invitedAt,
                            Instant inviteExpiresAt, Instant acceptedAt, Instant lastSeenAt, Instant revokedAt) {}

    public record InviteRequest(UUID clientId,
                                @NotBlank(message = "Choose the client") @Size(max = 255) String clientName,
                                boolean allProjects, @Size(max = 200) List<UUID> projectIds,
                                @NotBlank @Email(message = "Enter a valid email address") @Size(max = 255) String email,
                                @Size(max = 255) String contactName) {}

    public record ScopeRequest(boolean allProjects, @Size(max = 200) List<UUID> projectIds) {}

    // ── Staff side: inbox ─────────────────────────────────────────────────────
    /**
     * outcome ACCEPT turns the request into work: a QUESTION becomes a draft RFI, a CHANGE a draft change order, a
     * NEW_PROJECT a project in planning (projectType and budgetTotal then required). ANSWER replies and closes it;
     * DECLINE closes it. The response is shown to the client in every case.
     */
    public record DecideRequest(@NotBlank @Pattern(regexp = "ACCEPT|ANSWER|DECLINE") String outcome,
                                @NotBlank(message = "Write a reply for the client") @Size(max = 5000) String response,
                                String projectType, @DecimalMin(value = "0.01", message = "Budget must be greater than zero") BigDecimal budgetTotal,
                                LocalDate startDate, LocalDate endDate) {}

    public record AcceptUpload(
            @Pattern(regexp = "DRAWING|RFI|SUBMITTAL|CONTRACT|REPORT|PHOTO|GENERAL") String documentType,
            @NotBlank(message = "Give the document a title") @Size(max = 300) String title,
            @Size(max = 50) String documentNumber, @Size(max = 20) String revision,
            @Pattern(regexp = "INTERNAL|CLIENT") String visibility) {}

    public record RejectUpload(@NotBlank(message = "Say why the file is not accepted") @Size(max = 1000) String reason) {}
}
