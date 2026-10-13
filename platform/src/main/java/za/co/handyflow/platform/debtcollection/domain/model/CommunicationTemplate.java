package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * A versioned message template. Editing never mutates a published version: publish() creates the next
 * templateVersion and un-flags the previous one, so every sent Communication can point at the exact wording
 * used (templateCode + templateVersion). Placeholders look like {{debtorName}}; see TemplateRenderer.
 * (Field is "templateVersion", not "version", to avoid clashing with the aggregate's optimistic-lock field.)
 */
@Entity
@Table(name = "debtcollection_communication_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunicationTemplate extends AggregateRoot<CommunicationTemplate> {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CommunicationChannel channel;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** True for the current version of this code; exactly one per (tenant, code). */
    @Column(name = "is_latest", nullable = false)
    private boolean latest;

    @Column(name = "created_by")
    private UUID createdBy;

    public static CommunicationTemplate publish(TenantId tenantId, String code, int templateVersion,
                                                CommunicationChannel channel, String subject, String body, UUID createdBy) {
        if (code == null || !code.matches("[A-Z0-9_]{3,60}")) {
            throw new IllegalArgumentException("code must be 3-60 characters of A-Z, 0-9 or underscore");
        }
        if (channel == null || channel == CommunicationChannel.WHATSAPP) {
            throw new IllegalArgumentException("A template needs a channel; WhatsApp templates are not supported for debt collection");
        }
        if (body == null || body.isBlank()) throw new IllegalArgumentException("body is required");
        if (channel == CommunicationChannel.EMAIL && (subject == null || subject.isBlank())) {
            throw new IllegalArgumentException("An EMAIL template needs a subject");
        }
        CommunicationTemplate t = new CommunicationTemplate();
        t.initTenantId(tenantId);
        t.code = code;
        t.templateVersion = templateVersion;
        t.channel = channel;
        t.subject = subject;
        t.body = body;
        t.latest = true;
        t.createdBy = createdBy;
        return t;
    }

    public void supersede() {
        this.latest = false;
    }
}
