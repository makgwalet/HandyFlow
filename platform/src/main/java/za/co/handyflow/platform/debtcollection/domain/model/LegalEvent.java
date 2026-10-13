package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Append-only audit entry for a handover or a legal notice. actor null = system/integration. */
@Entity
@Table(name = "debtcollection_legal_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalEvent extends AggregateRoot<LegalEvent> {

    /** HANDOVER or NOTICE. */
    @Column(name = "subject_type", nullable = false, length = 15)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_user_name", length = 255)
    private String actorUserName;

    public static LegalEvent of(TenantId tenantId, String subjectType, UUID subjectId, UUID caseId, String eventType,
                                String detail, UUID actorUserId, String actorUserName) {
        LegalEvent e = new LegalEvent();
        e.initTenantId(tenantId);
        e.subjectType = subjectType;
        e.subjectId = subjectId;
        e.caseId = caseId;
        e.eventType = eventType;
        e.detail = detail;
        e.actorUserId = actorUserId;
        e.actorUserName = actorUserName;
        return e;
    }
}
