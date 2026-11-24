package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The result of checking one kind of document for an assessed obligation. */
@Entity
@Table(name = "compliance_assessment_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAssessmentEvidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private String result;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    public static ComplianceAssessmentEvidence of(TenantId tenantId, UUID itemId, String documentType, boolean mandatory, String result, String detail, LocalDate expiresOn) {
        ComplianceAssessmentEvidence e = new ComplianceAssessmentEvidence();
        e.tenantId = tenantId;
        e.itemId = itemId;
        e.documentType = documentType;
        e.mandatory = mandatory;
        e.result = result;
        e.detail = detail;
        e.expiresOn = expiresOn;
        return e;
    }
}
