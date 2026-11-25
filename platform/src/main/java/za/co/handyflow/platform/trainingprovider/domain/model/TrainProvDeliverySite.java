package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A campus or place where training or assessment is delivered. Approval is recorded per site, never assumed across sites. */
@Entity(name = "TrainProvDeliverySite")
@Table(name = "trainprov_delivery_sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvDeliverySite {

    public static final Set<String> TYPES = Set.of("CAMPUS", "DELIVERY_SITE", "ASSESSMENT_CENTRE", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "site_type", nullable = false)
    private String siteType;
    private String address;
    private String province;
    @Column(name = "contact_person")
    private String contactPerson;
    private String phone;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvDeliverySite create(TenantId tenantId, String name, String type, String address,
                                               String province, String contactPerson, String phone) {
        TrainProvDeliverySite s = new TrainProvDeliverySite();
        s.tenantId = tenantId.getValue();
        s.apply(name, type, address, province, contactPerson, phone);
        s.createdAt = Instant.now();
        return s;
    }

    public void update(String name, String type, String address, String province, String contactPerson, String phone) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("A retired site cannot be changed");
        }
        apply(name, type, address, province, contactPerson, phone);
    }

    private void apply(String name, String type, String address, String province, String contactPerson, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Site name is required");
        }
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("Site type must be one of " + String.join(", ", new java.util.TreeSet<>(TYPES)));
        }
        this.name = name.trim();
        this.siteType = type;
        this.address = blankToNull(address);
        this.province = blankToNull(province);
        this.contactPerson = blankToNull(contactPerson);
        this.phone = blankToNull(phone);
        this.updatedAt = Instant.now();
    }

    public void retire(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This site is already retired");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "RETIRED";
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
