package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of a client's own chart of accounts — mirrors {@code
 * accounting.AccAccount}'s shape, but scoped by {@code clientId} in
 * addition to {@code tenantId}, since here one tenant (the bookkeeping
 * practice) maintains a separate chart of accounts per client, not one
 * for itself. A standard SA chart is seeded per client on first use by
 * {@code BkAccountService} (same seed-on-first-use pattern {@code
 * AccountingService}'s own {@code coaSeeder} uses for a tenant).
 */
@Entity
@Table(name = "bk_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkAccount {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_type", nullable = false)
    private String accountType; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE

    @Column(name = "account_subtype")
    private String accountSubtype;

    @Column(nullable = false)
    private boolean system = false;

    @Column(name = "opening_balance", precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public static BkAccount create(TenantId tenantId, UUID clientId, String accountCode, String accountName,
                                    String accountType, String accountSubtype, boolean system, String description) {
        BkAccount a = new BkAccount();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.accountCode = accountCode;
        a.accountName = accountName;
        a.accountType = accountType;
        a.accountSubtype = accountSubtype;
        a.system = system;
        a.openingBalance = BigDecimal.ZERO;
        a.description = description;
        a.createdAt = Instant.now();
        return a;
    }
}
