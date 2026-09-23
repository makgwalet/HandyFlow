package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The first real cross-module reference in this module — deliberately a
 * reference, not a copy, per the source material's own explicit design
 * principle: this stores only {@code employeeId} and the role this
 * person plays ON THIS TENDER (e.g. "Project Manager"), never a name,
 * qualification, or experience summary. Those are looked up live from
 * {@code HrFacade.findEmployeeById(...)} whenever this is read (see
 * {@code TenderPersonnelService}) — if the employee's HR record changes
 * (a new qualification added, a name correction), every tender
 * referencing them shows the current data automatically, with nothing
 * to keep in sync.
 * <p>
 * A tender SUBMISSION still needs a frozen snapshot of what was actually
 * submitted, for audit — that's a deliberately separate, not-yet-built
 * concern (see the strategic roadmap backlog, Part 6): this table is the
 * live, editable-until-submission state, not the historical record of
 * what a specific submission contained.
 */
@Entity
@Table(name = "tender_personnel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderPersonnel {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId; // reference into HR — not a DB FK, HR lives in another module's table

    @Column(nullable = false)
    private String role; // this person's role ON THIS TENDER, e.g. "Project Manager" — not their HR job title

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public static TenderPersonnel create(TenantId tenantId, UUID tenderId, UUID employeeId, String role, UUID createdBy) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        TenderPersonnel p = new TenderPersonnel();
        p.tenantId = tenantId;
        p.tenderId = tenderId;
        p.employeeId = employeeId;
        p.role = role;
        p.createdAt = Instant.now();
        p.createdBy = createdBy;
        return p;
    }
}
