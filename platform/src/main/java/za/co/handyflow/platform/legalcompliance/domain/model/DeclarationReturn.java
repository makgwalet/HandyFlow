package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One person's answer to one campaign. The exact words they confirmed are stored with the answer. A person can change a "nothing to declare" or "unchanged" answer while
 * the campaign is open, but once they have declared an interest through the campaign they can no longer say they have nothing to declare.
 */
@Entity
@Table(name = "legalcompliance_declaration_returns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeclarationReturn extends AggregateRoot<DeclarationReturn> {

    public static final String NIL_STATEMENT = "I confirm that I have no interests, relationships or outside roles to declare.";
    public static final String UNCHANGED_STATEMENT = "I confirm that the declarations I have already made are complete and still accurate, and that I have nothing new to declare.";

    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "user_name", nullable = false, length = 255) private String userName;
    @Column(name = "user_email", length = 255) private String userEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ReturnStatus status = ReturnStatus.PENDING;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(columnDefinition = "TEXT") private String statement;
    @Column(name = "declaration_ids", columnDefinition = "TEXT") private String declarationIds;

    public static DeclarationReturn assign(TenantId t, UUID campaignId, UUID userId, String userName, String userEmail) {
        if (userId == null) throw new IllegalArgumentException("A user is required");
        DeclarationReturn r = new DeclarationReturn();
        r.initTenantId(t);
        r.campaignId = campaignId;
        r.userId = userId;
        r.userName = userName == null || userName.isBlank() ? "Unknown" : userName.trim();
        r.userEmail = userEmail;
        return r;
    }

    public boolean isPending() { return status == ReturnStatus.PENDING; }
    public boolean isLeft() { return status == ReturnStatus.LEFT; }

    /** Excuses someone who is no longer in the audience. Only an unanswered return: an answer is a record and stays. */
    public void markLeft() {
        if (status == ReturnStatus.LEFT) return;
        if (status != ReturnStatus.PENDING) throw new IllegalStateException("An answered return is kept as it is");
        this.status = ReturnStatus.LEFT;
    }

    /** Puts back someone who is in the audience again. */
    public void reinstate() {
        if (status != ReturnStatus.LEFT) throw new IllegalStateException("Only a return that was excused can be reinstated");
        this.status = ReturnStatus.PENDING;
    }

    private void requireInAudience() {
        if (status == ReturnStatus.LEFT) throw new IllegalStateException("You are no longer part of this campaign");
    }

    public void answerNil() {
        requireInAudience();
        requireNotDeclared("say you have nothing to declare");
        this.status = ReturnStatus.NIL;
        this.statement = NIL_STATEMENT;
        this.respondedAt = Instant.now();
    }

    /** existingCount: how many earlier declarations of theirs (not withdrawn, not refused) there are to confirm. */
    public void answerUnchanged(int existingCount) {
        requireInAudience();
        requireNotDeclared("confirm that nothing has changed");
        if (existingCount < 1) throw new IllegalArgumentException("You have no earlier declarations to confirm. Say you have nothing to declare, or declare an interest");
        this.status = ReturnStatus.UNCHANGED;
        this.statement = UNCHANGED_STATEMENT;
        this.respondedAt = Instant.now();
    }

    public void addDeclaration(UUID declarationId) {
        requireInAudience();
        List<UUID> ids = new ArrayList<>(declaredIds());
        if (!ids.contains(declarationId)) ids.add(declarationId);
        this.declarationIds = String.join(",", ids.stream().map(UUID::toString).toList());
        this.status = ReturnStatus.DECLARED;
        this.statement = null;
        this.respondedAt = Instant.now();
    }

    public List<UUID> declaredIds() {
        if (declarationIds == null || declarationIds.isBlank()) return List.of();
        return java.util.Arrays.stream(declarationIds.split(",")).filter(s -> !s.isBlank()).map(UUID::fromString).toList();
    }

    private void requireNotDeclared(String what) {
        if (status == ReturnStatus.DECLARED) throw new IllegalStateException("You have already declared an interest in this campaign, so you cannot " + what);
    }
}
