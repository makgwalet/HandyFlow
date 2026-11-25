package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An outside professional or firm. Never deleted: made inactive, with a reason, and brought back if needed. */
@Entity(name = "ComplianceProvider")
@Table(name = "compliance_providers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceProvider {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String kind = "INDIVIDUAL";
    private String email;
    private String phone;
    private String coverage;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "inactive_reason", columnDefinition = "TEXT")
    private String inactiveReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceProvider create(TenantId tenantId, String name, String kind, String email, String phone, String coverage, String notes, UUID by) {
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        ComplianceProvider p = new ComplianceProvider();
        p.tenantId = tenantId;
        p.createdBy = by;
        p.createdAt = Instant.now();
        p.edit(name, kind, email, phone, coverage, notes);
        return p;
    }

    public void edit(String name, String kind, String email, String phone, String coverage, String notes) {
        this.name = ProviderRules.required(name, "The name", 200);
        this.kind = ProviderRules.kind(kind);
        this.email = ProviderRules.email(email);
        this.phone = ProviderRules.optional(phone, "The phone number", 50);
        this.coverage = ProviderRules.optional(coverage, "The coverage", 500);
        this.notes = ProviderRules.optional(notes, "The notes", 4000);
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void deactivate(String reason) {
        if (!isActive()) throw new IllegalStateException("This provider is already inactive");
        this.inactiveReason = ProviderRules.note(reason);
        this.status = "INACTIVE";
    }

    public void reactivate() {
        if (isActive()) throw new IllegalStateException("This provider is already active");
        this.status = "ACTIVE";
        this.inactiveReason = null;
    }
}
