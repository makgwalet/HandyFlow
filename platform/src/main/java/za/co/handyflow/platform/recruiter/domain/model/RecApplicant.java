package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rec_applicants")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecApplicant {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name",  nullable = false) private String lastName;
    @Column(nullable = false)                       private String email;
    private String phone;
    private String location;
    @Column(name = "linkedin_url")  private String linkedinUrl;
    @Column(name = "portfolio_url") private String portfolioUrl;
    @Column(name = "cv_url")        private String cvUrl;
    @Column(name = "cv_name")       private String cvName;
    @Column(name = "portal_token",  unique = true) private String portalToken;
    @Column(name = "created_at")    private Instant createdAt;
    @Column(name = "updated_at")    private Instant updatedAt;
    @Column(name = "anonymised_at") private Instant anonymisedAt;

    public static RecApplicant create(TenantId tenantId, String firstName, String lastName,
                                       String email, String phone, String location,
                                       String linkedinUrl, String portfolioUrl,
                                       String cvUrl, String cvName) {
        RecApplicant a   = new RecApplicant();
        a.tenantId       = tenantId;
        a.firstName      = firstName;
        a.lastName       = lastName;
        a.email          = email.toLowerCase().trim();
        a.phone          = phone;
        a.location       = location;
        a.linkedinUrl    = linkedinUrl;
        a.portfolioUrl   = portfolioUrl;
        a.cvUrl          = cvUrl;
        a.cvName         = cvName;
        a.portalToken    = UUID.randomUUID().toString().replace("-","")
                         + UUID.randomUUID().toString().replace("-","");
        a.createdAt      = Instant.now();
        a.updatedAt      = Instant.now();
        return a;
    }

    public void updateCv(String cvUrl, String cvName) {
        this.cvUrl     = cvUrl;
        this.cvName    = cvName;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String phone, String location,
                               String linkedinUrl, String portfolioUrl) {
        if (phone        != null) this.phone        = phone;
        if (location     != null) this.location     = location;
        if (linkedinUrl  != null) this.linkedinUrl  = linkedinUrl;
        if (portfolioUrl != null) this.portfolioUrl = portfolioUrl;
        this.updatedAt = Instant.now();
    }

    /** Removes everything that identifies the person. The row stays, so counts and history of applications still add up. */
    public void anonymise() {
        this.firstName = "Removed";
        this.lastName = "applicant";
        this.email = "removed-" + id + "@removed.invalid";
        this.phone = null; this.location = null; this.linkedinUrl = null; this.portfolioUrl = null;
        this.cvUrl = null; this.cvName = null;
        this.portalToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        this.anonymisedAt = Instant.now();
        this.updatedAt = this.anonymisedAt;
    }

    public boolean isAnonymised() { return anonymisedAt != null; }

    public String getFullName() { return firstName + " " + lastName; }
}
