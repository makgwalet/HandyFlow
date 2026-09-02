package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A person nominated by a {@link TrainProvClient} to attend training —
 * deliberately its own entity, not a link into {@code hr.HrEmployee}
 * or Module 4a's own entities. Same reasoning
 * {@code PayEmployee}'s own Javadoc gives: this person doesn't work for
 * the provider's tenant, they work for {@code clientId}, a business
 * that may not be a HandyFlow tenant at all.
 */
@Entity
@Table(name = "trainprov_delegates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvDelegate {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "delegate_number", nullable = false)
    private String delegateNumber;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "id_number")
    private String idNumber;

    private String email;
    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    /** ACTIVE | INACTIVE */
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static TrainProvDelegate create(TenantId tenantId, UUID clientId, String delegateNumber, String fullName,
                                            String idNumber, String email, String phone, String jobTitle) {
        TrainProvDelegate d = new TrainProvDelegate();
        d.tenantId = tenantId.getValue();
        d.clientId = clientId;
        d.delegateNumber = delegateNumber;
        d.fullName = fullName;
        d.idNumber = idNumber;
        d.email = email;
        d.phone = phone;
        d.jobTitle = jobTitle;
        d.status = "ACTIVE";
        d.createdAt = Instant.now();
        d.updatedAt = Instant.now();
        return d;
    }

    public void update(String fullName, String idNumber, String email, String phone, String jobTitle) {
        this.fullName = fullName;
        this.idNumber = idNumber;
        this.email = email;
        this.phone = phone;
        this.jobTitle = jobTitle;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
