package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor
public class Project {

    @Id UUID id;
    @Column(name = "tenant_id",       nullable = false) UUID tenantId;
    @Column(name = "project_number",  nullable = false) String projectNumber;
    @Column(nullable = false)                           String name;
    String description;
    @Column(name = "project_type",    nullable = false) String projectType = "GENERAL";
    @Column(nullable = false)                           String status      = "PLANNING";
    @Column(nullable = false)                           String health      = "GREEN";

    @Column(name = "client_id")   UUID   clientId;
    @Column(name = "client_name") String clientName;
    @Column(name = "site_address") String siteAddress;

    @Column(name = "start_date")    LocalDate startDate;
    @Column(name = "end_date")      LocalDate endDate;
    @Column(name = "baseline_start") LocalDate baselineStart;
    @Column(name = "baseline_end")   LocalDate baselineEnd;

    @Column(name = "budget_total",     nullable = false) BigDecimal budgetTotal     = BigDecimal.ZERO;
    @Column(name = "budget_spent",     nullable = false) BigDecimal budgetSpent     = BigDecimal.ZERO;
    @Column(name = "budget_committed", nullable = false) BigDecimal budgetCommitted = BigDecimal.ZERO;

    @Column(name = "contract_value") BigDecimal contractValue;
    @Column(name = "contract_ref")   String contractRef;
    @Column(name = "contract_type")  String contractType;
    @Column(name = "retention_pct")  BigDecimal retentionPct = BigDecimal.ZERO;

    @Column(name = "cidb_grade")   String cidbGrade;
    @Column(name = "nhbrc_number") String nhbrcNumber;

    @Column(name = "project_manager_id")   UUID   projectManagerId;
    @Column(name = "project_manager_name") String projectManagerName;
    @Column(name = "client_portal_token")  String clientPortalToken;

    String notes;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;

    public static Project create(UUID tenantId, String projectNumber, String name,
                                 String projectType, UUID clientId, String clientName,
                                 LocalDate startDate, LocalDate endDate,
                                 BigDecimal budgetTotal, UUID createdBy) {
        Project p = new Project();
        p.id            = UUID.randomUUID();
        p.tenantId      = tenantId;
        p.projectNumber = projectNumber;
        p.name          = name;
        p.projectType   = projectType != null ? projectType : "GENERAL";
        p.clientId      = clientId;
        p.clientName    = clientName;
        p.startDate     = startDate;
        p.endDate       = endDate;
        p.baselineStart = startDate;
        p.baselineEnd   = endDate;
        p.budgetTotal   = budgetTotal != null ? budgetTotal : BigDecimal.ZERO;
        p.status        = "PLANNING";
        p.health        = "GREEN";
        p.clientPortalToken = UUID.randomUUID().toString().replace("-", "");
        p.createdBy     = createdBy;
        p.createdAt     = Instant.now();
        p.updatedAt     = Instant.now();
        return p;
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void activate()  { requireStatus("PLANNING","ON_HOLD"); this.status = "ACTIVE";    touch(); }
    public void hold()      { requireStatus("ACTIVE");              this.status = "ON_HOLD";   touch(); }
    public void complete()  { requireStatus("ACTIVE");              this.status = "COMPLETED"; this.completedAt = Instant.now(); touch(); }
    public void cancel(String reason) { this.status = "CANCELLED"; this.cancelledAt = Instant.now(); if(reason!=null)this.notes=(notes==null?"":notes+"\nCancelled: "+reason); touch(); }

    public void updateHealth() {
        // Auto-calc: budget > 10% over → RED, > 5% → AMBER
        // Schedule: endDate > baseline by > 14 days → RED, > 7 → AMBER
        boolean overBudget = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                && budgetSpent.add(budgetCommitted)
                .compareTo(budgetTotal.multiply(new java.math.BigDecimal("1.10"))) > 0;
        boolean atRisk = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                && budgetSpent.add(budgetCommitted)
                .compareTo(budgetTotal.multiply(new java.math.BigDecimal("1.05"))) > 0;
        boolean lateRed   = endDate != null && baselineEnd != null && endDate.isAfter(baselineEnd.plusDays(14));
        boolean lateAmber = endDate != null && baselineEnd != null && endDate.isAfter(baselineEnd.plusDays(7));

        if (overBudget || lateRed)   this.health = "RED";
        else if (atRisk || lateAmber) this.health = "AMBER";
        else                          this.health = "GREEN";
        touch();
    }

    public void setBudgetTotal(BigDecimal v)     { this.budgetTotal     = v; }
    public void setBudgetSpent(BigDecimal v)     { this.budgetSpent     = v; }
    public void setBudgetCommitted(BigDecimal v) { this.budgetCommitted = v; }
    public void setProjectManagerId(UUID v)      { this.projectManagerId   = v; }
    public void setProjectManagerName(String v)  { this.projectManagerName = v; }
    public void setEndDate(LocalDate v)          { this.endDate = v; }
    public void setName(String v)                { this.name = v; }
    public void setDescription(String v)         { this.description = v; }
    public void setNotes(String v)               { this.notes = v; }
    public void setCidbGrade(String v)           { this.cidbGrade = v; }
    public void setNhbrcNumber(String v)         { this.nhbrcNumber = v; }
    public void setContractValue(BigDecimal v)   { this.contractValue = v; }
    public void setContractRef(String v)         { this.contractRef = v; }

    private void requireStatus(String... allowed) {
        for (String s : allowed) if (s.equals(status)) return;
        throw new IllegalStateException("Cannot transition from " + status);
    }
    private void touch() { this.updatedAt = Instant.now(); }
}
