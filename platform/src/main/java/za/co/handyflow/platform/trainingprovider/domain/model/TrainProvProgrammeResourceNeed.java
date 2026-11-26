package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeNeedsRules;

import java.time.Instant;
import java.util.UUID;

/** A kind and quantity of resource a programme version lists as needed. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvProgrammeResourceNeed")
@Table(name = "trainprov_programme_resource_needs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgrammeResourceNeed {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private UUID programmeVersionId;
    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;
    @Column(nullable = false, updatable = false)
    private String description;
    @Column(name = "match_text", updatable = false)
    private String matchText;
    @Column(nullable = false, updatable = false)
    private int quantity;
    @Column(name = "per_learners", updatable = false)
    private Integer perLearners;
    @Column(name = "source_note", nullable = false, updatable = false)
    private String sourceNote;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvProgrammeResourceNeed create(TenantId t, UUID versionId, String type, String description, String matchText, Integer quantity, Integer perLearners,
                                                        String sourceNote, UUID by) {
        String problem = ProgrammeNeedsRules.problem(type, description, matchText, quantity, perLearners, sourceNote);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvProgrammeResourceNeed n = new TrainProvProgrammeResourceNeed();
        n.tenantId = t.getValue();
        n.programmeVersionId = versionId;
        n.resourceType = type;
        n.description = description.trim();
        n.matchText = matchText == null || matchText.isBlank() ? null : matchText.trim();
        n.quantity = quantity;
        n.perLearners = perLearners;
        n.sourceNote = sourceNote.trim();
        n.createdBy = by;
        n.createdAt = Instant.now();
        return n;
    }

    public void withdraw(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("That need is already withdrawn");
        }
        String problem = ProgrammeNeedsRules.withdrawProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        status = "WITHDRAWN";
        withdrawnReason = reason.trim();
        withdrawnBy = by;
        withdrawnAt = Instant.now();
    }
}
