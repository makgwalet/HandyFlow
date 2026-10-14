package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately keyed on supplier NAME, not a supplier_id — see this
 * table's own migration comment for why. Matches the pattern
 * ApPdfGenerator.generateSupplierStatement() already uses.
 */
@Entity
@Table(name = "ap_supplier_banking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApSupplierBanking {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "supplier_name", nullable = false) private String supplierName;
    @Column(name = "bank_name")      private String bankName;
    @Column(name = "account_holder") private String accountHolder;
    @Column(name = "account_number", nullable = false) private String accountNumber;
    @Column(name = "branch_code",    nullable = false) private String branchCode;
    @Column(name = "vat_number")     private String vatNumber;
    private String email;
    private String notes;

    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    // Finance patch F6: changed bank details must be checked by a second person before money is paid to them.
    @Column(name = "changed_by")        private UUID    changedBy;
    @Column(name = "changed_at")        private Instant changedAt;
    @Column(name = "verified_by")       private UUID    verifiedBy;
    @Column(name = "verified_at")       private Instant verifiedAt;
    @Column(name = "verification_note") private String  verificationNote;

    public static ApSupplierBanking create(TenantId tenantId, String supplierName,
                                           String bankName, String accountHolder,
                                           String accountNumber, String branchCode,
                                           String vatNumber, String email, String notes, UUID createdBy) {
        ApSupplierBanking b = new ApSupplierBanking();
        b.tenantId       = tenantId;
        b.supplierName   = supplierName;
        b.bankName       = bankName;
        b.accountHolder  = accountHolder;
        b.accountNumber  = accountNumber;
        b.branchCode     = branchCode;
        b.vatNumber      = vatNumber;
        b.email          = email;
        b.notes          = notes;
        b.createdBy      = createdBy;
        b.createdAt      = Instant.now();
        b.updatedAt      = Instant.now();
        b.changedBy      = createdBy;
        b.changedAt      = b.createdAt;
        return b;
    }

    /** True when money would now go somewhere different; the details then wait for a second person's check. */
    public boolean update(String bankName, String accountHolder, String accountNumber,
                          String branchCode, String vatNumber, String email, String notes, UUID changedBy) {
        boolean moneyChanges = za.co.handyflow.platform.ap.application.internal.ApDutyRules.bankingChanged(
                this.bankName, this.accountHolder, this.accountNumber, this.branchCode,
                bankName, accountHolder, accountNumber, branchCode);
        this.bankName      = bankName;
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
        this.branchCode    = branchCode;
        this.vatNumber     = vatNumber;
        this.email         = email;
        this.notes         = notes;
        this.updatedAt     = Instant.now();
        if (moneyChanges) {
            this.changedBy        = changedBy;
            this.changedAt        = this.updatedAt;
            this.verifiedBy       = null;
            this.verifiedAt       = null;
            this.verificationNote = null;
        }
        return moneyChanges;
    }

    public boolean isVerified() { return verifiedAt != null; }

    public void verify(UUID by, String note) {
        if (verifiedAt != null) throw new IllegalStateException("These bank details are already checked");
        this.verifiedBy       = by;
        this.verifiedAt       = Instant.now();
        this.verificationNote = note;
        this.updatedAt        = this.verifiedAt;
    }
}