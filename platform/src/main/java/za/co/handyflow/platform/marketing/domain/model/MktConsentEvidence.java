package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only proof-of-permission log. Rows are never updated or deleted:
 * every opt-in, opt-out, import declaration and suppression change is a new row.
 */
@Entity
@Table(name = "mkt_consent_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktConsentEvidence {

    public static final String OPT_IN             = "OPT_IN";
    public static final String OPT_OUT            = "OPT_OUT";
    public static final String IMPORT_DECLARATION = "IMPORT_DECLARATION";
    public static final String SUPPRESSION_ADDED  = "SUPPRESSION_ADDED";
    public static final String SUPPRESSION_LIFTED = "SUPPRESSION_LIFTED";

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "preference_id") private UUID preferenceId;
    @Column(nullable = false, length = 320) private String email;
    @Column(nullable = false, length = 20)  private String channel = "EMAIL";
    @Column(nullable = false, length = 30)  private String purpose = "MARKETING";
    @Column(name = "event_type", nullable = false, length = 30) private String eventType;
    @Column(length = 50) private String source;
    @Column(name = "evidence_text", columnDefinition = "text") private String evidenceText;
    @Column(name = "recorded_by") private UUID recordedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public static MktConsentEvidence record(TenantId tenantId, UUID preferenceId, String email,
                                            String eventType, String source,
                                            String evidenceText, UUID recordedBy) {
        MktConsentEvidence e = new MktConsentEvidence();
        e.tenantId     = tenantId;
        e.preferenceId = preferenceId;
        e.email        = email;
        e.eventType    = eventType;
        e.source       = source;
        e.evidenceText = evidenceText;
        e.recordedBy   = recordedBy;
        e.createdAt    = Instant.now();
        return e;
    }
}