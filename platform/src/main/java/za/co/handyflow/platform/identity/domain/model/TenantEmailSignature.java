package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-tenant email sign-off block. See migration V286 for the full
 * rationale. A missing row, or {@code enabled = false}, means no signature
 * is appended anywhere — this is opt-in, not a forced visual change to
 * every tenant's outbound email.
 */
@Entity
@Table(name = "tenant_email_signature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantEmailSignature {

    @Id
    @Column(name = "tenant_id")
    private java.util.UUID tenantId;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "enabled")
    private boolean enabled = false;

    public static TenantEmailSignature create(java.util.UUID tenantId, String displayName, String jobTitle,
                                               String phone, String email, String website) {
        TenantEmailSignature sig = new TenantEmailSignature();
        sig.tenantId = tenantId;
        sig.displayName = displayName;
        sig.jobTitle = jobTitle;
        sig.phone = phone;
        sig.email = email;
        sig.website = website;
        sig.enabled = true;
        return sig;
    }

    public void update(String displayName, String jobTitle, String phone, String email, String website) {
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.phone = phone;
        this.email = email;
        this.website = website;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
