package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One answered (or yet-to-be-answered) checklist question within an inspection. The question text is a snapshot. */
@Entity
@Table(name = "fleet_inspection_results")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InspectionResult {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "template_item_id")
    private UUID templateItemId;

    @Column(nullable = false, length = 100)
    private String section;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "photo_required_on_fail", nullable = false)
    private boolean photoRequiredOnFail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionOutcome outcome = InspectionOutcome.PENDING;

    @Column(columnDefinition = "TEXT")
    private String comment;

    static InspectionResult from(Inspection inspection, InspectionTemplateItem item) {
        InspectionResult r = new InspectionResult();
        r.inspection = inspection;
        r.templateItemId = item.getId();
        r.section = item.getSection();
        r.label = item.getLabel();
        r.sortOrder = item.getSortOrder();
        r.required = item.isRequired();
        r.photoRequiredOnFail = item.isPhotoRequiredOnFail();
        r.severity = item.getSeverity();
        return r;
    }

    void answer(InspectionOutcome outcome, String comment) {
        this.outcome = outcome;
        this.comment = comment;
    }

    void markNotApplicable() {
        this.outcome = InspectionOutcome.NOT_APPLICABLE;
    }
}