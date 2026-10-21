package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What happened to a letter, by whom and when. The database refuses to change or delete these. */
@Entity
@Table(name = "hr_letter_events")
@Getter
@NoArgsConstructor
public class HrLetterEvent {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "letter_id") UUID letterId;
    String action;
    String note;
    @Column(name = "done_by") UUID doneBy;
    @Column(name = "done_at") Instant doneAt;

    public static HrLetterEvent of(UUID tenantId, UUID letterId, String action, String note, UUID by) {
        HrLetterEvent e = new HrLetterEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.letterId = letterId;
        e.action = action;
        e.note = note != null && note.length() > 1000 ? note.substring(0, 1000) : note;
        e.doneBy = by;
        e.doneAt = Instant.now();
        return e;
    }
}
