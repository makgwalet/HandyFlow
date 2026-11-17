package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetControlMode;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A spending limit for one department / cost centre / project over a period. */
@Entity
@Table(name = "sc_budgets")
@Getter
@NoArgsConstructor
public class ScBudget {

    public static final String ACTIVE = "ACTIVE";
    public static final String CLOSED = "CLOSED";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(nullable = false, length = 200) String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20) BudgetScopeType scopeType;
    @Column(name = "scope_value", nullable = false, length = 100) String scopeValue;
    @Column(name = "period_start", nullable = false) LocalDate periodStart;
    @Column(name = "period_end", nullable = false) LocalDate periodEnd;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "control_mode", nullable = false, length = 10) BudgetControlMode controlMode;
    @Column(nullable = false, length = 10) String status = ACTIVE;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScBudget create(UUID tenantId, String name, BudgetScopeType scopeType, String scopeValue,
                                  LocalDate start, LocalDate end, BigDecimal amount, BudgetControlMode mode,
                                  String currency, UUID createdBy) {
        ScBudget b = new ScBudget();
        b.id = UUID.randomUUID();
        b.tenantId = tenantId;
        b.name = name;
        b.scopeType = scopeType;
        b.scopeValue = scopeValue;
        b.periodStart = start;
        b.periodEnd = end;
        b.amount = amount;
        b.controlMode = mode;
        b.currency = currency != null && !currency.isBlank() ? currency : "ZAR";
        b.createdBy = createdBy;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    public void update(String name, BigDecimal amount, BudgetControlMode mode) {
        if (!ACTIVE.equals(status)) throw new IllegalStateException("A closed budget cannot be changed");
        if (name != null) this.name = name;
        if (amount != null) this.amount = amount;
        if (mode != null) this.controlMode = mode;
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = CLOSED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return ACTIVE.equals(status); }
}
