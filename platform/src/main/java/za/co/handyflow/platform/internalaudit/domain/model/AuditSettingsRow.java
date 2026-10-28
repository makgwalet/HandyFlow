package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One company's own internal audit rules. No row means the standard rules. */
@Entity
@Table(name = "audit_settings")
@Getter
@NoArgsConstructor
public class AuditSettingsRow {
    @Id @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "low_max") private int lowMax;
    @Column(name = "medium_max") private int mediumMax;
    @Column(name = "high_max") private int highMax;
    @Column(name = "escalate_after_days") private int escalateAfterDays;
    @Column(name = "repeat_every_days") private int repeatEveryDays;
    @Column(name = "first_review_reminder_day") private int firstReviewReminderDay;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    public static AuditSettingsRow forTenant(UUID tenantId) {
        AuditSettingsRow r = new AuditSettingsRow();
        r.tenantId = tenantId;
        return r;
    }

    public void set(int lowMax, int mediumMax, int highMax, int escalateAfterDays, int repeatEveryDays, int firstReviewReminderDay, UUID by) {
        this.lowMax = lowMax; this.mediumMax = mediumMax; this.highMax = highMax;
        this.escalateAfterDays = escalateAfterDays; this.repeatEveryDays = repeatEveryDays; this.firstReviewReminderDay = firstReviewReminderDay;
        this.updatedBy = by; this.updatedAt = Instant.now();
    }
}
