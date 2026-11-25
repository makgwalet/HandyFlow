package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What a confirmed change means for one client and one obligation, and what was done about it. Never deleted, only moved on, and the time a notice was given is kept even if the impact is reopened. */
@Entity(name = "ComplianceRegImpact")
@Table(name = "compliance_reg_impacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRegImpact {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "change_id", nullable = false)
    private UUID changeId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(name = "handled_by")
    private UUID handledBy;
    @Column(name = "handled_at")
    private Instant handledAt;
    @Column(name = "notice_given_at")
    private Instant noticeGivenAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceRegImpact open(TenantId tenantId, UUID changeId, UUID clientId, UUID obligationId, LocalDate dueDate) {
        ComplianceRegImpact i = new ComplianceRegImpact();
        i.tenantId = tenantId;
        i.changeId = changeId;
        i.clientId = clientId;
        i.obligationId = obligationId;
        i.dueDate = dueDate;
        i.createdAt = Instant.now();
        return i;
    }

    /** Moves the impact on. A note of at least 15 characters is kept with every move, so the trail says what was done. */
    public void act(String action, String rawNote, UUID by) {
        String a = action == null ? "" : action.trim().toUpperCase();
        if (!RegulatoryRules.impactActions(status).contains(a)) throw new IllegalStateException("You cannot do that to an impact that is " + status.toLowerCase().replace('_', ' '));
        if (by == null) throw new IllegalArgumentException("A person is required");
        String n = RegulatoryRules.reviewNote(rawNote);
        if ("REOPEN".equals(a)) {
            this.status = "OPEN";
        } else {
            this.status = a;
            if ("NOTICE_GIVEN".equals(a)) this.noticeGivenAt = Instant.now();
        }
        this.note = n;
        this.handledBy = by;
        this.handledAt = Instant.now();
    }
}
