package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Contact {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String email;
    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "is_primary")
    private boolean primary = false;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static Contact create(TenantId tenantId, Customer customer,
                                 String firstName, String lastName,
                                 String email, String phone,
                                 String jobTitle, boolean primary) {
        Contact c = new Contact();
        c.tenantId = tenantId;
        c.customer = customer;
        c.firstName = firstName.trim();
        c.lastName = lastName.trim();
        c.email = email;
        c.phone = phone;
        c.jobTitle = jobTitle;
        c.primary = primary;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}