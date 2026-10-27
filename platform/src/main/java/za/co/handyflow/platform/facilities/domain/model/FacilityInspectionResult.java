package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One answered (or still pending) check within an inspection; the prompt is copied from the template. */
@Entity
@Table(name = "facility_inspection_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityInspectionResult {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 300)
    private String prompt;

    @Column(nullable = false)
    private boolean critical;

    /** PASS, FAIL or NA; null while unanswered. */
    @Column(length = 10)
    private String outcome;

    @Column(length = 1000)
    private String comment;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    static FacilityInspectionResult from(FacilityInspectionTemplateItem item) {
        FacilityInspectionResult r = new FacilityInspectionResult();
        r.itemId = item.getId();
        r.position = item.getPosition();
        r.prompt = item.getPrompt();
        r.critical = item.isCritical();
        return r;
    }

    void answer(String outcome, String comment) {
        this.outcome = outcome;
        this.comment = comment;
        this.recordedAt = Instant.now();
    }

    public boolean isFailed() { return "FAIL".equals(outcome); }
}
