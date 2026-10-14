package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A reusable rule: statement lines that fit are booked to {@code otherAccountId} (finance patch F12). */
@Entity
@Table(name = "acc_bank_rules")
@Getter
@NoArgsConstructor
public class AccBankRule {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    String name;
    @Column(name = "bank_account_id")  UUID bankAccountId;
    String direction = "ANY";
    @Column(name = "match_text")       String matchText;
    @Column(name = "match_field")      String matchField = "EITHER";
    @Column(name = "min_amount")       BigDecimal minAmount;
    @Column(name = "max_amount")       BigDecimal maxAmount;
    @Column(name = "other_account_id") UUID otherAccountId;
    String description;
    int priority = 100;
    boolean active = true;
    @Column(name = "times_applied")    int timesApplied;
    @Column(name = "created_by")       UUID createdBy;
    @Column(name = "created_at")       Instant createdAt;
    @Column(name = "updated_at")       Instant updatedAt;

    public static AccBankRule create(TenantId tenantId, UUID createdBy) {
        AccBankRule r = new AccBankRule();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId.getValue();
        r.createdBy = createdBy;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void define(String name, UUID bankAccountId, String direction, String matchText, String matchField,
                       BigDecimal minAmount, BigDecimal maxAmount, UUID otherAccountId, String description, int priority) {
        this.name = name.trim();
        this.bankAccountId = bankAccountId;
        this.direction = direction == null ? "ANY" : direction;
        this.matchText = matchText == null || matchText.trim().isEmpty() ? null : matchText.trim();
        this.matchField = matchField == null ? "EITHER" : matchField;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.otherAccountId = otherAccountId;
        this.description = description == null || description.trim().isEmpty() ? null : description.trim();
        this.priority = priority;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void activate()   { this.active = true;  this.updatedAt = Instant.now(); }
    public void applied()    { this.timesApplied++; this.updatedAt = Instant.now(); }
}
