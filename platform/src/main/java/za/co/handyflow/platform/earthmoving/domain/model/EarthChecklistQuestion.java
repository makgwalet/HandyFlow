package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** One question on a checklist version. */
@Entity
@Table(name = "earthmoving_checklist_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthChecklistQuestion {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false)
    private int position;

    private String section;

    @Column(nullable = false)
    private String text;

    @Column(name = "answer_type", nullable = false)
    private String answerType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "min_value", precision = 14, scale = 3)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 14, scale = 3)
    private BigDecimal maxValue;

    private String unit;

    @Column(name = "fail_outcome", nullable = false)
    private String failOutcome = InspectionRules.DEFECT;

    public static EarthChecklistQuestion of(TenantId tenantId, UUID versionId, int position, ChecklistRules.Question q) {
        EarthChecklistQuestion x = new EarthChecklistQuestion();
        x.tenantId = tenantId;
        x.versionId = versionId;
        x.position = position;
        x.section = q.section();
        x.text = q.text();
        x.answerType = q.answerType();
        x.required = q.required();
        x.builtIn = q.builtIn();
        x.minValue = q.min();
        x.maxValue = q.max();
        x.unit = q.unit();
        x.failOutcome = q.failOutcome();
        return x;
    }

    public ChecklistRules.Question toQuestion() {
        return new ChecklistRules.Question(id, section, text, answerType, required, builtIn, minValue, maxValue, unit, failOutcome);
    }
}
