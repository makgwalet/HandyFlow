package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One step in a case's life, kept for good. */
@Entity
@Table(name = "compliance_case_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceCaseEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private String event;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    private UUID actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public static ComplianceCaseEvent of(TenantId tenantId, UUID caseId, String event, String from, String to, String note, UUID actor) {
        ComplianceCaseEvent e = new ComplianceCaseEvent();
        e.tenantId = tenantId;
        e.caseId = caseId;
        e.event = event;
        e.fromStatus = from;
        e.toStatus = to;
        e.note = note;
        e.actor = actor;
        return e;
    }
}
