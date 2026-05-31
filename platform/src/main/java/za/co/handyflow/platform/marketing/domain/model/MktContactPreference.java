package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mkt_contact_preferences")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktContactPreference {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id")                      private UUID   entityId;
    @Column(nullable = false)                         private String email;
    private String name;

    @Column(name = "email_opted_in",     nullable = false) private boolean emailOptedIn     = false;
    @Column(name = "sms_opted_in",       nullable = false) private boolean smsOptedIn       = false;
    @Column(name = "whatsapp_opted_in",  nullable = false) private boolean whatsappOptedIn  = false;

    @Column(name = "email_opted_in_at")  private Instant emailOptedInAt;
    @Column(name = "email_opted_out_at") private Instant emailOptedOutAt;
    @Column(name = "opt_in_source")      private String  optInSource;
    @Column(name = "unsubscribe_token",  unique = true) private String unsubscribeToken;

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static MktContactPreference create(TenantId tenantId, String entityType,
                                               UUID entityId, String email, String name,
                                               boolean emailOptedIn, String optInSource) {
        MktContactPreference p = new MktContactPreference();
        p.tenantId          = tenantId;
        p.entityType        = entityType;
        p.entityId          = entityId;
        p.email             = email.toLowerCase().trim();
        p.name              = name;
        p.emailOptedIn      = emailOptedIn;
        p.optInSource       = optInSource;
        p.unsubscribeToken  = UUID.randomUUID().toString().replace("-","")
                            + UUID.randomUUID().toString().replace("-","");
        if (emailOptedIn) p.emailOptedInAt = Instant.now();
        p.createdAt         = Instant.now();
        p.updatedAt         = Instant.now();
        return p;
    }

    public void optIn(String source) {
        this.emailOptedIn    = true;
        this.emailOptedInAt  = Instant.now();
        this.emailOptedOutAt = null;
        this.optInSource     = source;
        this.updatedAt       = Instant.now();
    }

    public void optOut() {
        this.emailOptedIn    = false;
        this.emailOptedOutAt = Instant.now();
        this.updatedAt       = Instant.now();
    }
}
