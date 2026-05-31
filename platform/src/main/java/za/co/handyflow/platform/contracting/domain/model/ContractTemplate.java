package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "contract_templates")
@Getter
@NoArgsConstructor
public class ContractTemplate {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    String name;
    @Column(name = "contract_type") String contractType;
    String description;
    @Column(name = "body_template", columnDefinition = "TEXT") String bodyTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, String> variables;

    @Column(name = "is_system") boolean isSystem;
    boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Version long version;

    public static ContractTemplate create(TenantId tenantId, String name,
                                          String contractType, String description,
                                          String bodyTemplate, Map<String, String> variables,
                                          boolean isSystem) {
        ContractTemplate t = new ContractTemplate();
        t.id           = UUID.randomUUID();
        t.tenantId     = tenantId.getValue();
        t.name         = name;
        t.contractType = contractType;
        t.description  = description;
        t.bodyTemplate = bodyTemplate;
        t.variables    = variables;
        t.isSystem     = isSystem;
        t.active       = true;
        t.createdAt    = Instant.now();
        t.updatedAt    = Instant.now();
        return t;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active    = false;
        this.updatedAt = Instant.now();
    }
}