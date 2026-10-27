package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One check on an inspection template. A failed {@code critical} item is treated as a safety/compliance failure. */
@Entity
@Table(name = "facility_inspection_template_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityInspectionTemplateItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 300)
    private String prompt;

    @Column(nullable = false)
    private boolean critical;

    static FacilityInspectionTemplateItem of(int position, String prompt, boolean critical) {
        FacilityInspectionTemplateItem i = new FacilityInspectionTemplateItem();
        i.position = position;
        i.prompt = prompt.trim();
        i.critical = critical;
        return i;
    }
}
