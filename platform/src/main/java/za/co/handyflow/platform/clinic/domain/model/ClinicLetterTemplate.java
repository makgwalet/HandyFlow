package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A reusable letter preset. Archived, never deleted. */
@Entity
@Table(name = "clinic_letter_templates")
@Getter
@NoArgsConstructor
public class ClinicLetterTemplate {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID    tenantId;
    String kind;
    String name;
    String title;
    String body;
    String specialty;
    String urgency;
    @Column(name = "unfit_days")  Integer unfitDays;
    @Column(name = "created_by")  UUID    createdBy;
    @Column(name = "created_at")  Instant createdAt;
    @Column(name = "updated_at")  Instant updatedAt;
    @Column(name = "archived_at") Instant archivedAt;

    public static ClinicLetterTemplate create(TenantId t, String kind, String name, String title, String body,
                                              String specialty, String urgency, Integer unfitDays, UUID by) {
        var x = new ClinicLetterTemplate();
        x.id = UUID.randomUUID(); x.tenantId = t.getValue(); x.kind = kind; x.createdBy = by;
        x.createdAt = Instant.now();
        x.update(name, title, body, specialty, urgency, unfitDays);
        return x;
    }

    public void update(String name, String title, String body, String specialty, String urgency, Integer unfitDays) {
        this.name = name; this.title = title; this.body = body; this.specialty = specialty; this.urgency = urgency;
        this.unfitDays = unfitDays; this.updatedAt = Instant.now();
    }

    public boolean isArchived() { return archivedAt != null; }
    public void archive() { if (archivedAt == null) archivedAt = Instant.now(); }
}
