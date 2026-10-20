package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "fleet_inspection_template_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InspectionTemplateItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private InspectionTemplate template;

    @Column(nullable = false, length = 100)
    private String section;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "photo_required_on_fail", nullable = false)
    private boolean photoRequiredOnFail = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSeverity severity = IssueSeverity.MEDIUM;

    static InspectionTemplateItem of(InspectionTemplate template, TemplateItemSpec spec, int sortOrder) {
        InspectionTemplateItem i = new InspectionTemplateItem();
        i.template = template;
        i.section = spec.section();
        i.label = spec.label();
        i.sortOrder = sortOrder;
        i.required = spec.required();
        i.photoRequiredOnFail = spec.photoRequiredOnFail();
        i.severity = spec.severity();
        return i;
    }
}