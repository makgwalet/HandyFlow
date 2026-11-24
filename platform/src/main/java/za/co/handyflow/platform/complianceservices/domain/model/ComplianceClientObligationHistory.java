package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One entry in the story of why an obligation applies, or does not, to a client. Never changed after it is written. */
@Entity
@Table(name = "compliance_client_obligation_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClientObligationHistory {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(nullable = false)
    private String event;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private UUID actor;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static ComplianceClientObligationHistory of(TenantId tenantId, UUID assignmentId, String from, String to, String event,
                                                       String reason, UUID actor, int versionNumber) {
        ComplianceClientObligationHistory h = new ComplianceClientObligationHistory();
        h.tenantId = tenantId;
        h.assignmentId = assignmentId;
        h.fromState = from;
        h.toState = to;
        h.event = event;
        h.reason = ClientProfileRules.clean(reason, 2000);
        h.actor = actor;
        h.versionNumber = versionNumber;
        h.occurredAt = Instant.now();
        return h;
    }
}
