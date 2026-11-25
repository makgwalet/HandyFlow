package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** One SMS handed to the provider for one deadline, to one consenting number. At most one of each kind. "Handed over" is not "delivered". */
@Entity(name = "ComplianceSmsLog")
@Table(name = "compliance_sms_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceSmsLog {
    public static final Set<String> OUTCOMES = Set.of("HANDED_OVER", "FAILED");

    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "deadline_id", nullable = false)
    private UUID deadlineId;
    @Column(name = "consent_id", nullable = false)
    private UUID consentId;
    @Column(nullable = false)
    private String kind;
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;
    @Column(nullable = false)
    private String outcome;
    private String detail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceSmsLog of(TenantId tenantId, UUID clientId, UUID deadlineId, UUID consentId, String kind, LocalDate runDate, String outcome, String detail) {
        if (!OUTCOMES.contains(outcome)) throw new IllegalArgumentException("Unknown outcome");
        ComplianceSmsLog l = new ComplianceSmsLog();
        l.tenantId = tenantId;
        l.clientId = clientId;
        l.deadlineId = deadlineId;
        l.consentId = consentId;
        l.kind = kind;
        l.runDate = runDate;
        l.outcome = outcome;
        l.detail = detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail;
        l.createdAt = Instant.now();
        return l;
    }
}
