package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A supplier's bank account (B04). It is never edited: a change is a NEW record, proposed by one person and verified (or rejected)
 * by someone else, and only a verified account counts. Verifying a new account retires the one before it, so the history of
 * which details were valid when is always kept. The database also refuses a verifier who is the proposer.
 */
@Entity
@Table(name = "fuel_supplier_bank_accounts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSupplierBankAccount {

    public static final Set<String> TYPES = Set.of("CURRENT", "SAVINGS", "TRANSMISSION", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(name = "account_holder", nullable = false, length = 150, updatable = false)
    private String accountHolder;

    @Column(name = "bank_name", nullable = false, length = 100, updatable = false)
    private String bankName;

    @Column(name = "branch_code", length = 20, updatable = false)
    private String branchCode;

    @Column(name = "account_number", nullable = false, length = 30, updatable = false)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, length = 20, updatable = false)
    private String accountType = "CURRENT";

    @Column(nullable = false, length = 24)
    private String status = "PENDING_VERIFICATION";

    @Column(length = 300, updatable = false)
    private String reason;

    @Column(name = "proposed_by", nullable = false, updatable = false)
    private UUID proposedBy;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Version
    private Long version;

    public static FuelSupplierBankAccount propose(TenantId tenantId, UUID supplierId, String holder, String bank, String branch, String account,
                                                  String type, String reason, UUID by) {
        if (by == null) throw new IllegalStateException("You are not signed in as a user.");
        if (holder == null || holder.isBlank()) throw new IllegalArgumentException("Enter the account holder's name.");
        if (bank == null || bank.isBlank()) throw new IllegalArgumentException("Enter the bank name.");
        String digits = account == null ? "" : account.replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]{5,20}")) throw new IllegalArgumentException("The account number must be 5 to 20 digits.");
        String br = branch == null ? "" : branch.replaceAll("\\s", "");
        if (!br.isEmpty() && !br.matches("[A-Za-z0-9-]{1,20}")) throw new IllegalArgumentException("The branch code can only hold letters, digits and dashes.");
        String t = type == null || type.isBlank() ? "CURRENT" : type.trim().toUpperCase();
        if (!TYPES.contains(t)) throw new IllegalArgumentException("Account type must be CURRENT, SAVINGS, TRANSMISSION or OTHER.");
        FuelSupplierBankAccount a = new FuelSupplierBankAccount();
        a.tenantId = tenantId; a.supplierId = supplierId; a.proposedBy = by;
        a.accountHolder = cut(holder.trim(), 150); a.bankName = cut(bank.trim(), 100); a.branchCode = br.isEmpty() ? null : br;
        a.accountNumber = digits; a.accountType = t; a.reason = reason == null || reason.isBlank() ? null : cut(reason.trim(), 300);
        return a;
    }

    public boolean isPending() { return "PENDING_VERIFICATION".equals(status); }
    public boolean isVerified() { return "VERIFIED".equals(status); }

    /** The same bank, branch and number as another account. */
    public boolean sameDetailsAs(FuelSupplierBankAccount o) {
        return accountNumber.equals(o.accountNumber) && bankName.equalsIgnoreCase(o.bankName) && java.util.Objects.equals(branchCode, o.branchCode);
    }

    /** Someone other than the proposer confirms the details (how they did so goes in the note), and the account starts to count. */
    public void verify(UUID by, String note) {
        decide(by, note, "Say how you confirmed these details (at least 5 characters).");
        this.status = "VERIFIED";
    }

    public void reject(UUID by, String note) {
        decide(by, note, "Give a reason of at least 5 characters.");
        this.status = "REJECTED";
    }

    /** Only the person who proposed it can take it back, and only while it waits. */
    public void withdraw(UUID by) {
        if (!isPending()) throw new IllegalStateException("Only a proposal that is still waiting can be withdrawn.");
        if (by == null || !by.equals(proposedBy)) throw new IllegalStateException("Only the person who proposed these details can withdraw them.");
        this.status = "WITHDRAWN"; this.withdrawnAt = Instant.now();
    }

    /** A newer verified account replaces this one. The history is kept. */
    public void supersede() {
        if (!isVerified()) throw new IllegalStateException("Only a verified account can be replaced.");
        this.status = "SUPERSEDED"; this.supersededAt = Instant.now();
    }

    private void decide(UUID by, String note, String noteMessage) {
        if (!isPending()) throw new IllegalStateException("These details have already been decided.");
        if (by == null) throw new IllegalStateException("You are not signed in as a user.");
        if (by.equals(proposedBy)) throw new IllegalStateException("Bank details must be decided by someone other than the person who proposed them.");
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException(noteMessage);
        this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = cut(note.trim(), 300);
    }

    /** "****1234": enough to recognise an account, not enough to pay into it. */
    public String maskedNumber() {
        return "****" + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }

    private static String cut(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
}
