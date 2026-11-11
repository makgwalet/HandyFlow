package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Who to call when something goes wrong: paramedics, the fire department, the venue manager,
 * the security company. A phone number is mandatory — a contact you can't ring is worse than none,
 * because it makes the list look complete.
 */
@Entity
@Table(name = "event_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventContact {

    public static final Set<String> TYPES = Set.of(
            "MEDICAL", "FIRE", "POLICE", "SECURITY", "VENUE", "ORGANISER", "SUPPLIER", "OTHER");

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)    private UUID tenantId;
    @Column(name = "event_id", nullable = false)     private UUID eventId;
    @Column(name = "contact_type", nullable = false) private String contactType;
    @Column(name = "name", nullable = false)         private String name;
    @Column(name = "organisation")                   private String organisation;
    @Column(name = "phone", nullable = false)        private String phone;
    @Column(name = "alternate_phone")                private String alternatePhone;
    @Column(name = "email")                          private String email;
    @Column(name = "notes")                          private String notes;
    @Column(name = "created_at", nullable = false)   private Instant createdAt;
    @Column(name = "updated_at", nullable = false)   private Instant updatedAt;

    public static EventContact create(TenantId tenantId, UUID eventId, String contactType, String name,
                                      String organisation, String phone, String alternatePhone,
                                      String email, String notes) {
        EventContact c = new EventContact();
        c.id        = UUID.randomUUID();
        c.tenantId  = tenantId.getValue();
        c.eventId   = eventId;
        c.createdAt = Instant.now();
        c.apply(contactType, name, organisation, phone, alternatePhone, email, notes);
        return c;
    }

    public void update(String contactType, String name, String organisation, String phone,
                       String alternatePhone, String email, String notes) {
        apply(contactType, name, organisation, phone, alternatePhone, email, notes);
    }

    private void apply(String contactType, String name, String organisation, String phone,
                       String alternatePhone, String email, String notes) {
        String type = contactType == null ? "" : contactType.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (!TYPES.contains(type))
            throw new IllegalArgumentException("Unknown contact type '" + contactType + "'. Valid: " + TYPES);
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("A contact needs a name");
        if (phone == null || phone.isBlank())
            throw new IllegalArgumentException("A contact needs a phone number — an emergency contact you can't ring is no use");
        this.contactType    = type;
        this.name           = name.trim();
        this.organisation   = organisation;
        this.phone          = phone.trim();
        this.alternatePhone = alternatePhone;
        this.email          = email;
        this.notes          = notes;
        this.updatedAt      = Instant.now();
    }
}