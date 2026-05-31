package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mkt_templates")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktTemplate {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String  name;
    @Column(nullable = false) private String  subject;
    @Column(nullable = false) private String  htmlBody;
    @Column(name = "preview_text") private String previewText;
    private String  category;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_by") private UUID    createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static MktTemplate create(TenantId tenantId, String name, String subject,
                                      String htmlBody, String previewText,
                                      String category, UUID createdBy) {
        MktTemplate t   = new MktTemplate();
        t.tenantId      = tenantId;
        t.name          = name;
        t.subject       = subject;
        t.htmlBody      = htmlBody;
        t.previewText   = previewText;
        t.category      = category;
        t.createdBy     = createdBy;
        t.active        = true;
        t.createdAt     = Instant.now();
        t.updatedAt     = Instant.now();
        return t;
    }

    public void update(String name, String subject, String htmlBody,
                        String previewText, String category) {
        if (name        != null) this.name        = name;
        if (subject     != null) this.subject     = subject;
        if (htmlBody    != null) this.htmlBody    = htmlBody;
        if (previewText != null) this.previewText = previewText;
        if (category    != null) this.category    = category;
        this.updatedAt  = Instant.now();
    }
}
