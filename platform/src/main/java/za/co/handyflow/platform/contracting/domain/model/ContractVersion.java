package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One numbered snapshot of a contract's body. Never changed after it is written. */
@Entity
@Table(name = "contract_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractVersion {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "version_no") private int versionNo;
    @Column(columnDefinition = "TEXT") private String body;
    private String source;
    private String note;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractVersion of(UUID tenantId, UUID contractId, int versionNo, String body, String source, String note, UUID by) {
        ContractVersion v = new ContractVersion();
        v.id = UUID.randomUUID();
        v.tenantId = tenantId;
        v.contractId = contractId;
        v.versionNo = versionNo;
        v.body = body == null ? "" : body;
        v.source = source;
        v.note = note;
        v.createdBy = by;
        v.createdAt = Instant.now();
        return v;
    }
}
