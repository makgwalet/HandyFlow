package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The wording of a letter, with {{merge fields}}. Each save raises the revision, and a letter remembers the revision it was made from. */
@Entity
@Table(name = "hr_letter_templates")
@Getter
@NoArgsConstructor
public class HrLetterTemplate {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    String code;
    String name;
    String category;
    String subject;
    String body;
    @Column(name = "requires_ack") boolean requiresAck;
    boolean active = true;
    boolean customised;
    int revision = 1;
    @Column(name = "updated_by") UUID updatedBy;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrLetterTemplate fromDefault(UUID tenantId, LetterDefaults.Template d) {
        HrLetterTemplate t = new HrLetterTemplate();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.code = d.code();
        t.name = d.name();
        t.category = d.category();
        t.subject = d.subject();
        t.body = d.body();
        t.requiresAck = d.requiresAck();
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    public static HrLetterTemplate custom(UUID tenantId, String code, String name, String category, String subject, String body,
                                          boolean requiresAck, UUID by) {
        HrLetterTemplate t = new HrLetterTemplate();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.code = code;
        t.name = name.trim();
        t.category = category;
        t.subject = subject.trim();
        t.body = body;
        t.requiresAck = requiresAck;
        t.customised = true;
        t.updatedBy = by;
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    public void edit(String name, String category, String subject, String body, boolean requiresAck, boolean active, UUID by) {
        this.name = name.trim();
        this.category = category;
        this.subject = subject.trim();
        this.body = body;
        this.requiresAck = requiresAck;
        this.active = active;
        this.customised = true;
        this.revision++;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public void restore(LetterDefaults.Template d, UUID by) {
        this.name = d.name();
        this.category = d.category();
        this.subject = d.subject();
        this.body = d.body();
        this.requiresAck = d.requiresAck();
        this.customised = false;
        this.revision++;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
