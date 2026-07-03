package za.co.handyflow.platform.notifications.application;

import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything {@link NotificationService#send} needs to raise a notification.
 * Built via {@link #builder()} — with this many optional fields, a
 * telescoping constructor would be unreadable at call sites.
 */
public final class NotificationRequest {

    private final TenantId tenantId;
    private final NotificationType type;
    private final NotificationSeverity severity;      // null => use type.defaultSeverity()
    private final Set<NotificationChannel> channels;   // null => use type.defaultChannels()
    private final String title;
    private final String message;
    private final String actionUrl;
    private final String sourceModule;
    private final String sourceEntityId;
    private final List<Recipient> recipients;

    private NotificationRequest(Builder b) {
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId is required");
        this.type = Objects.requireNonNull(b.type, "type is required");
        this.severity = b.severity;
        this.channels = b.channels;
        this.title = Objects.requireNonNull(b.title, "title is required");
        this.message = Objects.requireNonNull(b.message, "message is required");
        this.actionUrl = b.actionUrl;
        this.sourceModule = Objects.requireNonNull(b.sourceModule, "sourceModule is required");
        this.sourceEntityId = b.sourceEntityId;
        if (b.recipients == null || b.recipients.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        this.recipients = List.copyOf(b.recipients);
    }

    public TenantId tenantId() { return tenantId; }
    public NotificationType type() { return type; }
    public NotificationSeverity severity() { return severity != null ? severity : type.defaultSeverity(); }
    public Set<NotificationChannel> channels() { return channels != null ? channels : type.defaultChannels(); }
    public String title() { return title; }
    public String message() { return message; }
    public String actionUrl() { return actionUrl; }
    public String sourceModule() { return sourceModule; }
    public String sourceEntityId() { return sourceEntityId; }
    public List<Recipient> recipients() { return recipients; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TenantId tenantId;
        private NotificationType type;
        private NotificationSeverity severity;
        private Set<NotificationChannel> channels;
        private String title;
        private String message;
        private String actionUrl;
        private String sourceModule;
        private String sourceEntityId;
        private List<Recipient> recipients;

        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder type(NotificationType type) { this.type = type; return this; }
        public Builder severity(NotificationSeverity severity) { this.severity = severity; return this; }
        public Builder channels(Set<NotificationChannel> channels) { this.channels = channels; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder actionUrl(String actionUrl) { this.actionUrl = actionUrl; return this; }
        public Builder sourceModule(String sourceModule) { this.sourceModule = sourceModule; return this; }
        public Builder sourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; return this; }
        public Builder recipients(List<Recipient> recipients) { this.recipients = recipients; return this; }
        public Builder recipient(Recipient recipient) { this.recipients = List.of(recipient); return this; }

        public NotificationRequest build() { return new NotificationRequest(this); }
    }
}