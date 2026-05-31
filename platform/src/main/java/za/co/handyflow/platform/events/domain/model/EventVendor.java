package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_vendors")
@Getter
@NoArgsConstructor
public class EventVendor {

    @Id UUID id;
    @Column(name = "tenant_id")           UUID tenantId;
    @Column(name = "event_id")            UUID eventId;
    @Column(name = "vendor_type")         String vendorType;
    @Column(name = "company_name")        String companyName;
    @Column(name = "contact_name")        String contactName;
    @Column(name = "contact_phone")       String contactPhone;
    @Column(name = "contact_email")       String contactEmail;
    @Column(name = "service_description") String serviceDescription;
    @Column(name = "quoted_amount")       BigDecimal quotedAmount;
    boolean confirmed;
    String notes;
    @Column(name = "created_at")          Instant createdAt;
    @Column(name = "updated_at")          Instant updatedAt;

    public static EventVendor create(TenantId tenantId, UUID eventId,
                                     String vendorType, String companyName,
                                     String contactName, String contactPhone,
                                     String contactEmail, String serviceDescription,
                                     BigDecimal quotedAmount, String notes) {
        EventVendor v = new EventVendor();
        v.id                 = UUID.randomUUID();
        v.tenantId           = tenantId.getValue();
        v.eventId            = eventId;
        v.vendorType         = vendorType;
        v.companyName        = companyName;
        v.contactName        = contactName;
        v.contactPhone       = contactPhone;
        v.contactEmail       = contactEmail;
        v.serviceDescription = serviceDescription;
        v.quotedAmount       = quotedAmount;
        v.confirmed          = false;
        v.notes              = notes;
        v.createdAt          = Instant.now();
        v.updatedAt          = Instant.now();
        return v;
    }

    public void confirm() {
        this.confirmed = true;
        this.updatedAt = Instant.now();
    }
}