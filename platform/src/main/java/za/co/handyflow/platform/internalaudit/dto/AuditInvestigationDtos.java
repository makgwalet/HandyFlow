package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditInvestigationDtos {
    private AuditInvestigationDtos() {}

    public record OpenRequest(String title, String summary) {}
    public record MemberRequest(UUID userId, String role) {}
    public record NoteRequest(String text) {}
    public record EvidenceRequest(String description, String source) {}
    public record TransferRequest(UUID toUserId, String reason) {}
    public record StatusRequest(boolean underReview) {}
    public record CloseRequest(String reason) {}

    public record Summary(UUID id, String number, String title, String status, String myRole, Instant openedAt, int members) {}
    public record MemberView(UUID userId, String name, String role, Instant addedAt) {}
    public record EvidenceView(UUID id, String ref, String description, String source, Instant collectedAt, UUID holderId, String holderName) {}
    public record LogView(int seq, Instant at, String actorName, String action, String detail, String hash) {}
    public record View(Summary summary, String summaryText, String closeReason, Instant closedAt, List<MemberView> members, List<EvidenceView> evidence, List<LogView> log) {}
    public record Verification(boolean ok, int entries, Integer brokenAtSeq) {}
}
