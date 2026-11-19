package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single, assessable requirement within one version of an instrument.
 * The applicability conditions (min employees, industries, activities) feed the SUGGESTION only;
 * they never assign the requirement to anyone. An empty industries/activities list means
 * "this condition is not recorded", NOT "applies to everyone".
 */
@Entity
@Table(name = "legalcompliance_regulatory_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryRequirement {

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "instrument_id", nullable = false) private UUID instrumentId;
    @Column(name = "version_id", nullable = false) private UUID versionId;
    @Column(nullable = false, length = 100) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "requirement_text", nullable = false, columnDefinition = "TEXT") private String requirementText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ObligationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceInterval recurrence;

    @Column(name = "min_employees") private Integer minEmployees;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> industries = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> activities = new ArrayList<>();

    @Column(name = "evidence_expectation", columnDefinition = "TEXT") private String evidenceExpectation;
    @Column(name = "previous_requirement_id") private UUID previousRequirementId;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @jakarta.persistence.Version private Long version;

    public static RegulatoryRequirement create(UUID tenantId, UUID instrumentId, UUID versionId, String reference,
                                               String title, String text, ObligationCategory category,
                                               RecurrenceInterval recurrence, Integer minEmployees,
                                               List<String> industries, List<String> activities,
                                               String evidenceExpectation, UUID previousRequirementId, UUID createdBy) {
        RegulatoryRequirement r = new RegulatoryRequirement();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.instrumentId = instrumentId;
        r.versionId = versionId;
        r.reference = reference;
        r.title = title;
        r.requirementText = text;
        r.category = category;
        r.recurrence = recurrence != null ? recurrence : RecurrenceInterval.ANNUALLY;
        r.minEmployees = minEmployees;
        r.industries = clean(industries);
        r.activities = clean(activities);
        r.evidenceExpectation = evidenceExpectation;
        r.previousRequirementId = previousRequirementId;
        r.createdBy = createdBy;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public void update(String reference, String title, String text, ObligationCategory category,
                       RecurrenceInterval recurrence, Integer minEmployees, List<String> industries,
                       List<String> activities, String evidenceExpectation) {
        this.reference = reference;
        this.title = title;
        this.requirementText = text;
        this.category = category;
        this.recurrence = recurrence != null ? recurrence : RecurrenceInterval.ANNUALLY;
        this.minEmployees = minEmployees;
        this.industries = clean(industries);
        this.activities = clean(activities);
        this.evidenceExpectation = evidenceExpectation;
        this.updatedAt = Instant.now();
    }

    /** Copy into a new draft version, remembering which requirement it came from (change tracking). */
    public RegulatoryRequirement carryForwardTo(UUID newVersionId, UUID by) {
        return create(tenantId, instrumentId, newVersionId, reference, title, requirementText, category, recurrence,
                minEmployees, industries, activities, evidenceExpectation, this.id, by);
    }

    private static List<String> clean(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) if (s != null && !s.isBlank() && !out.contains(s.trim().toUpperCase())) out.add(s.trim().toUpperCase());
        return out;
    }
}
