package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One controlled release of documents. The owning module keeps its own business record and is identified
 * here by (moduleCode, sourceType, sourceId), for example ("PROJECTS", "TRANSMITTAL", transmittalId).
 * contextId is an optional grouping the module chooses (for Projects, the project id).
 */
@Entity
@Table(name = "sda_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureShare {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "module_code", nullable = false)     private String moduleCode;
    @Column(name = "source_type", nullable = false)     private String sourceType;
    @Column(name = "source_id", nullable = false)       private UUID sourceId;
    @Column(name = "context_id")                        private UUID contextId;
    @Column(name = "created_by")                        private UUID createdBy;
    @Column(name = "created_by_name")                   private String createdByName;
    @Column(name = "created_at", nullable = false)      private Instant createdAt;

    public static SecureShare create(UUID tenantId, String moduleCode, String sourceType, UUID sourceId,
                                     UUID contextId, UUID createdBy, String createdByName) {
        SecureShare s = new SecureShare();
        s.id            = UUID.randomUUID();
        s.tenantId      = tenantId;
        s.moduleCode    = moduleCode;
        s.sourceType    = sourceType;
        s.sourceId      = sourceId;
        s.contextId     = contextId;
        s.createdBy     = createdBy;
        s.createdByName = createdByName;
        s.createdAt     = Instant.now();
        return s;
    }
}
