package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An individual staff member registered to make debt-collection contact
 * on this agency's behalf — the Debt Collectors Act requires each PERSON
 * doing collection work to hold their own registration, separate from
 * (and in addition to) the firm's own registration on CollAgencyProfile.
 * <p>
 * userId is an optional, unvalidated reference to an identity.User — this
 * module deliberately does not depend on `identity` (see package-info),
 * so fullName is captured directly as a snapshot, the same
 * "responsibleUserName" denormalization convention used throughout this
 * engagement (e.g. RegulatoryObligation.responsibleUserName) rather than
 * pulling in a facade dependency for one display string.
 */
@Entity
@Table(name = "collagency_collectors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyCollector {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "registration_number")
    private String registrationNumber; // individual Council for Debt Collectors registration

    @Column(name = "registration_expiry_date")
    private LocalDate registrationExpiryDate;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static CollAgencyCollector create(UUID tenantId, UUID userId, String fullName,
                                              String registrationNumber, LocalDate registrationExpiryDate,
                                              String email, String phone) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        CollAgencyCollector c = new CollAgencyCollector();
        c.tenantId = tenantId;
        c.userId = userId;
        c.fullName = fullName;
        c.registrationNumber = registrationNumber;
        c.registrationExpiryDate = registrationExpiryDate;
        c.email = email;
        c.phone = phone;
        c.active = true;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String fullName, String registrationNumber, LocalDate registrationExpiryDate,
                        String email, String phone) {
        this.fullName = fullName;
        this.registrationNumber = registrationNumber;
        this.registrationExpiryDate = registrationExpiryDate;
        this.email = email;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
