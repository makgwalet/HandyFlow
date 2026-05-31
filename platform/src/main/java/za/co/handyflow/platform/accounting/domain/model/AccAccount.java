package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acc_accounts")
@Getter
@NoArgsConstructor
public class AccAccount {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "account_code")   String accountCode;
    @Column(name = "account_name")   String accountName;
    @Column(name = "account_type")   String accountType;
    @Column(name = "account_subtype") String accountSubtype;
    @Column(name = "parent_id")      UUID parentId;
    @Column(name = "is_system")      boolean isSystem;
    boolean active = true;
    @Column(name = "opening_balance") BigDecimal openingBalance = BigDecimal.ZERO;
    String description;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static AccAccount create(TenantId tenantId, String code, String name,
                                    String type, String subtype, boolean isSystem) {
        AccAccount a = new AccAccount();
        a.id             = UUID.randomUUID();
        a.tenantId       = tenantId.getValue();
        a.accountCode    = code;
        a.accountName    = name;
        a.accountType    = type;
        a.accountSubtype = subtype;
        a.isSystem       = isSystem;
        a.active         = true;
        a.openingBalance = BigDecimal.ZERO;
        a.createdAt      = Instant.now();
        a.updatedAt      = Instant.now();
        return a;
    }
}