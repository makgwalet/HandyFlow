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
 * One of a client's own bank accounts — mirrors {@code
 * accounting.AccBankAccount} exactly, scoped additionally by
 * {@code clientId}. {@code accountId} links it to the matching
 * {@link BkAccount} (that client's own Chart of Accounts) once set,
 * exactly like {@code AccBankAccount.linkAccount()} — required before
 * reconciliation match-candidates can be suggested.
 */
@Entity
@Table(name = "bk_bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkBankAccount {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "account_type", nullable = false)
    private String accountType = "CURRENT";

    private String currency = "ZAR";

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static BkBankAccount create(TenantId tenantId, UUID clientId, String bankName, String accountName,
                                        String accountNumber, String branchCode, String accountType) {
        BkBankAccount b = new BkBankAccount();
        b.tenantId = tenantId;
        b.clientId = clientId;
        b.bankName = bankName;
        b.accountName = accountName;
        b.accountNumber = accountNumber;
        b.branchCode = branchCode;
        b.accountType = accountType != null ? accountType : "CURRENT";
        b.currency = "ZAR";
        b.currentBalance = BigDecimal.ZERO;
        b.active = true;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    public void linkAccount(UUID accountId) {
        this.accountId = accountId;
        this.updatedAt = Instant.now();
    }

    public void updateBalance(BigDecimal newBalance) {
        this.currentBalance = newBalance;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
