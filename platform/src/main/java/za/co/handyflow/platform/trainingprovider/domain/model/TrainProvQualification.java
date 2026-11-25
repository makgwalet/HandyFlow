package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;

import java.time.Instant;
import java.util.UUID;

/** The official reference for a qualification, skills programme or unit standard, as an authority registers it. Retired, never deleted. */
@Entity(name = "TrainProvQualification")
@Table(name = "trainprov_qualifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvQualification {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String title;
    @Column(name = "qualification_type", nullable = false)
    private String qualificationType;
    @Column(name = "official_ref")
    private String officialRef;
    @Column(name = "saqa_id")
    private String saqaId;
    @Column(name = "sub_framework")
    private String subFramework;
    @Column(name = "nqf_level")
    private Integer nqfLevel;
    private Integer credits;
    @Column(name = "authority_id")
    private UUID authorityId;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvQualification create(TenantId t, String title, String type, String officialRef, String saqaId,
                                                String subFramework, Integer nqfLevel, Integer credits, UUID authorityId, UUID by) {
        TrainProvQualification q = new TrainProvQualification();
        q.tenantId = t.getValue();
        q.createdBy = by;
        q.apply(title, type, officialRef, saqaId, subFramework, nqfLevel, credits, authorityId);
        q.createdAt = Instant.now();
        return q;
    }

    public void update(String title, String type, String officialRef, String saqaId, String subFramework,
                       Integer nqfLevel, Integer credits, UUID authorityId) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("A retired qualification cannot be changed");
        }
        apply(title, type, officialRef, saqaId, subFramework, nqfLevel, credits, authorityId);
    }

    private void apply(String title, String type, String officialRef, String saqaId, String subFramework,
                       Integer nqfLevel, Integer credits, UUID authorityId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Qualification title is required");
        }
        if (type == null || !CatalogueRules.QUALIFICATION_TYPES.contains(type)) {
            throw new IllegalArgumentException("Qualification type is not recognised");
        }
        if (nqfLevel != null && (nqfLevel < 1 || nqfLevel > 10)) {
            throw new IllegalArgumentException("NQF level must be between 1 and 10");
        }
        if (credits != null && credits < 0) {
            throw new IllegalArgumentException("Credits cannot be negative");
        }
        this.title = title.trim();
        this.qualificationType = type;
        this.officialRef = blank(officialRef);
        this.saqaId = blank(saqaId);
        this.subFramework = blank(subFramework);
        this.nqfLevel = nqfLevel;
        this.credits = credits;
        this.authorityId = authorityId;
        this.updatedAt = Instant.now();
    }

    public void retire(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This qualification is already retired");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "RETIRED";
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
