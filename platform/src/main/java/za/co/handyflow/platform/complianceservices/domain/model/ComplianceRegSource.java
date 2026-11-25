package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Where a regulatory change comes from: a statute, a regulator's notices, a guidance series. */
@Entity(name = "ComplianceRegSource")
@Table(name = "compliance_reg_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRegSource {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private String name;
    private String authority;
    private String url;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceRegSource create(TenantId tenantId, String name, String authority, String url, String kind, UUID by) {
        ComplianceRegSource s = new ComplianceRegSource();
        s.tenantId = tenantId;
        s.createdBy = by;
        s.createdAt = Instant.now();
        s.edit(name, authority, url, kind);
        return s;
    }

    public void edit(String name, String authority, String url, String kind) {
        this.name = RegulatoryRules.text(name, "Name", 200, true);
        this.authority = RegulatoryRules.text(authority, "Authority", 200, false);
        this.url = RegulatoryRules.url(url);
        this.kind = RegulatoryRules.kind(kind);
    }

    public void setActive(boolean active) { this.active = active; }
}
