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
 *
 * FIXED: this was previously a hand-written class with record-style accessor
 * names (tenantId(), type(), ...) but was NOT an actual Java record. To
 * Jackson — which only recognizes properties via getX()/isX() bean naming or
 * native record introspection — that meant this class had ZERO visible
 * properties: an "empty bean". That was invisible until Spring Modulith's
 * event publication registry (spring-modulith-events-jpa) tried to persist
 * this object as the payload of NotificationDispatchEvent, to support
 * redelivering notifications on restart if the app crashes between commit
 * and dispatch — and Jackson threw InvalidDefinitionException instead.
 *
 * Converting to a genuine record fixes serialization AND keeps that
 * restart-safety guarantee (the alternative — disabling FAIL_ON_EMPTY_BEANS,
 * or excluding this event from tracking — would silently give up on
 * redelivery instead of actually fixing the underlying issue).
 *
 * Builder API is unchanged — every existing call site
 * (NotificationRequest.builder()....build()) compiles and behaves
 * identically; only the internal representation changed.
 */
public record NotificationRequest(
        TenantId tenantId,
        NotificationType type,
        NotificationSeverity severity,
        Set<NotificationChannel> channels,
        String title,
        String message,
        String actionUrl,
        String sourceModule,
        String sourceEntityId,
        List<Recipient> recipients
) {
    public NotificationRequest {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(sourceModule, "sourceModule is required");
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        recipients = List.copyOf(recipients);
    }

    // Explicit overrides of the synthesized record accessors: severity and
    // channels fall back to the NotificationType's defaults when the caller
    // didn't specify one. The raw stored value can be null; these two
    // methods are where the fallback logic lives — same behavior as before.
    @Override
    public NotificationSeverity severity() {
        return severity != null ? severity : type.defaultSeverity();
    }

    @Override
    public Set<NotificationChannel> channels() {
        return channels != null ? channels : type.defaultChannels();
    }

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

        public NotificationRequest build() {
            return new NotificationRequest(tenantId, type, severity, channels, title,
                    message, actionUrl, sourceModule, sourceEntityId, recipients);
        }
    }
}