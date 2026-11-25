package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an instrument's history. Written once, never changed. */
@Entity(name = "TrainProvAssessmentInstrumentEvent")
@Table(name = "trainprov_assessment_instrument_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentInstrumentEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(updatable = false)
    private String note;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvAssessmentInstrumentEvent of(TenantId t, UUID instrumentId, String action, String note, UUID by) {
        TrainProvAssessmentInstrumentEvent e = new TrainProvAssessmentInstrumentEvent();
        e.tenantId = t.getValue();
        e.instrumentId = instrumentId;
        e.action = action;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}
