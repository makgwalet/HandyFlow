package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A Chart of Accounts entry. Maps to acc_coa_accounts — a table that
 * already existed with no application-layer code at all, same
 * situation as acc_periods and acc_fica_documents before them.
 * <p>
 * Read-focused (no mutators) — COA setup/management isn't in scope for
 * trial balance or journal account-name resolution, the two features
 * that actually need this. Both are read operations against an
 * existing, presumably-seeded chart, not COA administration.
 */
@Entity(name = "AccountantCoaAccount")
@Table(name = "acc_coa_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccCoaAccount {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "account_code", nullable = false, length = 20)  private String accountCode;
    @Column(name = "account_name", nullable = false, length = 200) private String accountName;
    @Column(name = "account_type", nullable = false, length = 20)  private String accountType;
    @Column(name = "sub_type", length = 50) private String subType;
    @Column(name = "vat_applicable", nullable = false) private boolean vatApplicable = false;
    @Column(name = "vat_type", length = 10)  private String vatType;
    @Column(name = "tax_schedule", length = 30) private String taxSchedule;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "active", nullable = false) private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    /**
     * NEW: closes the "minimal COA-seeding capability" gap — trial
     * balance needed something real to compute against. Was read-only
     * (no factory/mutators) until now, since account-name resolution
     * and trial balance are both read operations against an assumed-
     * existing chart; this is the first feature that actually creates
     * chart-of-accounts entries.
     */
    public static AccCoaAccount create(UUID tenantId, UUID clientId, String accountCode, String accountName,
                                       String accountType, String subType, boolean vatApplicable, String vatType) {
        AccCoaAccount a = new AccCoaAccount();
        a.tenantId       = tenantId;
        a.clientId       = clientId;
        a.accountCode    = accountCode;
        a.accountName    = accountName;
        a.accountType    = accountType;
        a.subType        = subType;
        a.vatApplicable  = vatApplicable;
        a.vatType        = vatType;
        a.createdAt      = Instant.now();
        return a;
    }
}