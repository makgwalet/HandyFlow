package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Resolves the personnel-reference design question left open in Phase 5
 * (see this module's own package-info.java): a client-scoped tender's
 * key personnel are the SERVICE PROVIDER's own staff, put forward on the
 * client's behalf — the same real-world shape as an accounting practice
 * using its own qualified people's credentials to help a client win a
 * bid. {@code employeeId} therefore references the SAME tenant's own
 * {@code hr} records {@code compliancetender.TenderPersonnel} already
 * references, not a separate concept of "the client's own personnel" —
 * a client company in this module has no HR records of its own to
 * reference in the first place; it's a company record in
 * {@code ComplianceClient}, not a HandyFlow tenant with employees.
 * <p>
 * Same reference-not-copy design as {@code TenderPersonnel}: only
 * {@code employeeId} and the role this person plays on THIS tender are
 * stored, never a name or qualification — those are looked up live from
 * {@code HrFacade} whenever this is read.
 */
@Entity
@Table(name = "client_tender_personnel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientTenderPersonnel {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_tender_id", nullable = false)
    private UUID clientTenderId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public static ClientTenderPersonnel create(TenantId tenantId, UUID clientTenderId, UUID employeeId,
                                               String role, UUID createdBy) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        ClientTenderPersonnel p = new ClientTenderPersonnel();
        p.tenantId = tenantId;
        p.clientTenderId = clientTenderId;
        p.employeeId = employeeId;
        p.role = role;
        p.createdAt = Instant.now();
        p.createdBy = createdBy;
        return p;
    }
}
