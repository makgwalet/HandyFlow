package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * How long one category of one module's records is kept, in one jurisdiction. {@code confirmed} records that the compliance owner
 * set or checked the period; an unconfirmed row, and the 7-year default when there is no row, show as "not confirmed".
 */
@Entity
@Table(name = "document_retention_policies")
public class RetentionPolicy implements RetentionRules.PolicyView {

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)   private UUID tenantId;
    @Column(name = "module_code", nullable = false, updatable = false) private String moduleCode;
    @Column(name = "category", nullable = false, updatable = false)    private String category;
    @Column(name = "jurisdiction", nullable = false, updatable = false) private String jurisdiction;
    @Column(name = "retain_years", nullable = false)                   private int retainYears;
    @Column(name = "personal_info", nullable = false)                  private boolean personalInfo;
    @Column(name = "confirmed", nullable = false)                      private boolean confirmed;
    @Column(name = "notes")                                            private String notes;
    @Column(name = "updated_by")                                       private UUID updatedBy;
    @Column(name = "updated_by_name")                                  private String updatedByName;
    @Column(name = "updated_at", nullable = false)                     private Instant updatedAt;

    protected RetentionPolicy() {}

    public static RetentionPolicy create(UUID tenantId, String module, String category, String jurisdiction) {
        RetentionPolicy p = new RetentionPolicy();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.moduleCode = RetentionRules.code(module, 40, "module");
        p.category = RetentionRules.code(category, 60, "category");
        p.jurisdiction = RetentionRules.jurisdiction(jurisdiction);
        p.retainYears = RetentionRules.DEFAULT_YEARS;
        return p;
    }

    public void set(int years, boolean personalInfo, boolean confirmed, String notes, UUID by, String byName) {
        this.retainYears = RetentionRules.years(years);
        this.personalInfo = personalInfo;
        this.confirmed = confirmed;
        this.notes = RetentionRules.optional(notes, 500);
        this.updatedBy = by;
        this.updatedByName = RetentionRules.optional(byName, 200);
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    @Override public String getModuleCode() { return moduleCode; }
    @Override public String getCategory() { return category; }
    @Override public String getJurisdiction() { return jurisdiction; }
    @Override public int getRetainYears() { return retainYears; }
    @Override public boolean isPersonalInfo() { return personalInfo; }
    @Override public boolean isConfirmed() { return confirmed; }
    public String getNotes() { return notes; }
    public UUID getUpdatedBy() { return updatedBy; }
    public String getUpdatedByName() { return updatedByName; }
    public Instant getUpdatedAt() { return updatedAt; }
}
