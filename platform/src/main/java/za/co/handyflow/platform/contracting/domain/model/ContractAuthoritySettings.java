package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Whether this organisation enforces delegated authority. Off until an administrator turns it on. */
@Entity
@Table(name = "contract_authority_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAuthoritySettings {
    @Id @Column(name = "tenant_id") private UUID tenantId;
    private boolean enforced;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractAuthoritySettings create(UUID tenantId) {
        ContractAuthoritySettings s = new ContractAuthoritySettings();
        s.tenantId = tenantId; s.updatedAt = Instant.now();
        return s;
    }

    public void set(boolean enforced, UUID by) { this.enforced = enforced; this.updatedBy = by; this.updatedAt = Instant.now(); }
}
