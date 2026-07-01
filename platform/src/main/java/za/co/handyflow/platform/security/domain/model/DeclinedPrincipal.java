// security/domain/model/DeclinedPrincipal.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DeclinedPrincipal — a formal record that the company declined to take an
 * engagement for this principal based on vetting findings.
 *
 * Distinct from Principal.deactivate() — deactivation is an operational
 * status ("no longer an active client"); this is a compliance decision
 * ("we will not work with this person, and here's why"). The distinction
 * matters for regulatory records: a company may need to demonstrate that
 * a particular client was declined due to a sanctions hit or PEP status,
 * and that record should survive even if the principal is later deleted.
 *
 * encryptedDetail stores the sensitive intelligence behind the decision —
 * encrypted with FieldEncryptionService, same as Principal.medicalNotes.
 */
@Entity
@Table(name = "security_declined_principals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeclinedPrincipal {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    @Column(name = "declined_at", nullable = false)
    private LocalDate declinedAt;

    @Column(name = "declined_by")
    private UUID declinedBy;

    @Column(nullable = false)
    private String reason;

    @Column(name = "encrypted_detail")
    private String encryptedDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static DeclinedPrincipal decline(TenantId tenantId, UUID principalId,
                                            UUID declinedBy, String reason,
                                            String encryptedDetail) {
        DeclinedPrincipal d  = new DeclinedPrincipal();
        d.tenantId           = tenantId;
        d.principalId        = principalId;
        d.declinedAt         = LocalDate.now();
        d.declinedBy         = declinedBy;
        d.reason             = reason;
        d.encryptedDetail    = encryptedDetail;
        d.createdAt          = Instant.now();
        return d;
    }
}
