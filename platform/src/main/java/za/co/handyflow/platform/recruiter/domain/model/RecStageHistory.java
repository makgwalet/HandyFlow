package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rec_stage_history")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecStageHistory {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "application_id", nullable = false) private UUID    applicationId;
    @Column(name = "from_stage")                        private String  fromStage;
    @Column(name = "to_stage",       nullable = false)  private String  toStage;
    @Column(name = "changed_by_name")                   private String  changedByName;
    private String  notes;
    @Column(name = "created_at") private Instant createdAt;

    public static RecStageHistory create(UUID applicationId, String fromStage,
                                          String toStage, String changedByName, String notes) {
        RecStageHistory h  = new RecStageHistory();
        h.applicationId    = applicationId;
        h.fromStage        = fromStage;
        h.toStage          = toStage;
        h.changedByName    = changedByName;
        h.notes            = notes;
        h.createdAt        = Instant.now();
        return h;
    }
}
