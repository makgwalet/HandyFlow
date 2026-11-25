package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One module of a programme version. Only changed while the version is a draft. */
@Entity(name = "TrainProvProgrammeModule")
@Table(name = "trainprov_programme_modules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgrammeModule {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(nullable = false)
    private Integer seq;
    @Column(nullable = false)
    private String title;
    @Column(name = "module_type", nullable = false)
    private String moduleType;
    private Integer credits;
    @Column(name = "notional_hours")
    private BigDecimal notionalHours;
    @Column(name = "is_required", nullable = false)
    private boolean required = true;
    @Column(name = "learning_outcomes")
    private String learningOutcomes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TrainProvProgrammeModule create(TenantId t, UUID versionId, int seq, String title, String type,
                                                  Integer credits, BigDecimal hours, boolean required, String outcomes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Module title is required");
        }
        if (type == null || !CatalogueRules.MODULE_TYPES.contains(type)) {
            throw new IllegalArgumentException("Module type must be KNOWLEDGE, PRACTICAL or WORKPLACE");
        }
        if ((credits != null && credits < 0) || (hours != null && hours.signum() < 0)) {
            throw new IllegalArgumentException("Credits and hours cannot be negative");
        }
        TrainProvProgrammeModule m = new TrainProvProgrammeModule();
        m.tenantId = t.getValue();
        m.programmeVersionId = versionId;
        m.seq = seq;
        m.title = title.trim();
        m.moduleType = type;
        m.credits = credits;
        m.notionalHours = hours;
        m.required = required;
        m.learningOutcomes = outcomes == null || outcomes.isBlank() ? null : outcomes.trim();
        m.createdAt = Instant.now();
        return m;
    }
}
