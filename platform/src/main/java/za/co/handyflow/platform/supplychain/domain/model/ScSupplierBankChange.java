package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A proposed change to a supplier's bank details. The proposed account number is stored
 * encrypted and only decrypted at the moment it is applied; the supplier record itself is
 * untouched until a different person verifies the change out-of-band.
 */
@Entity
@Table(name = "sc_supplier_bank_changes")
@Getter
@NoArgsConstructor
public class ScSupplierBankChange {

    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_id", nullable = false) UUID supplierId;
    @Column(nullable = false, length = 24) String status;
    @Column(name = "proposed_bank_name", nullable = false, length = 255) String proposedBankName;
    @Column(name = "proposed_account_enc", nullable = false, columnDefinition = "TEXT") String proposedAccountEnc;
    @Column(name = "proposed_account_masked", nullable = false, length = 40) String proposedAccountMasked;
    @Column(name = "proposed_branch_code", length = 20) String proposedBranchCode;
    @Column(name = "previous_bank_name", length = 255) String previousBankName;
    @Column(name = "previous_account_masked", length = 40) String previousAccountMasked;
    @Column(nullable = false, length = 500) String reason;
    @Column(length = 1000) String warnings;
    @Column(name = "requested_by") UUID requestedBy;
    @Column(name = "requested_by_name", length = 255) String requestedByName;
    @Column(name = "verified_by") UUID verifiedBy;
    @Column(name = "verified_by_name", length = 255) String verifiedByName;
    @Column(name = "verified_at") Instant verifiedAt;
    @Column(name = "verification_method", length = 30) String verificationMethod;
    @Column(name = "verification_note", length = 500) String verificationNote;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScSupplierBankChange propose(UUID tenantId, UUID supplierId, String bankName,
                                               String accountEnc, String accountMasked, String branchCode,
                                               String previousBankName, String previousAccountMasked,
                                               String reason, String warnings, UUID by, String byName) {
        ScSupplierBankChange c = new ScSupplierBankChange();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.supplierId = supplierId;
        c.status = PENDING_VERIFICATION;
        c.proposedBankName = bankName;
        c.proposedAccountEnc = accountEnc;
        c.proposedAccountMasked = accountMasked;
        c.proposedBranchCode = branchCode;
        c.previousBankName = previousBankName;
        c.previousAccountMasked = previousAccountMasked;
        c.reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        c.warnings = warnings != null && warnings.length() > 1000 ? warnings.substring(0, 1000) : warnings;
        c.requestedBy = by;
        c.requestedByName = byName;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public boolean isPending() { return PENDING_VERIFICATION.equals(status); }

    public void markApplied(UUID by, String byName, String method, String note) {
        close(APPLIED, by, byName, method, note);
    }

    public void markRejected(UUID by, String byName, String note) {
        close(REJECTED, by, byName, null, note);
    }

    public void markCancelled(UUID by, String byName, String note) {
        close(CANCELLED, by, byName, null, note);
    }

    private void close(String newStatus, UUID by, String byName, String method, String note) {
        if (!isPending()) throw new IllegalStateException("Bank change is already " + status);
        this.status = newStatus;
        this.verifiedBy = by;
        this.verifiedByName = byName;
        this.verifiedAt = Instant.now();
        this.verificationMethod = method;
        this.verificationNote = note != null && note.length() > 500 ? note.substring(0, 500) : note;
        this.updatedAt = Instant.now();
    }
}
