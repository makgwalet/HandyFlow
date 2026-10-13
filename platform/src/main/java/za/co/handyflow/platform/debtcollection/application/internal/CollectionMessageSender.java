package za.co.handyflow.platform.debtcollection.application.internal;

import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Set;
import java.util.UUID;

/**
 * Adapter contract for actually transmitting a message. CommunicationService discovers every bean implementing
 * this and routes by channel; if none supports a channel the communication is recorded as FAILED with a clear
 * "no sender configured" detail — it never pretends to have sent.
 * <p>
 * Implement one adapter per provider (an email service, an SMS gateway) and register it as a Spring bean.
 * Do NOT implement WHATSAPP unless eligibility has been confirmed with Meta (see CommunicationPolicy).
 * After sending, the provider's delivery callbacks should call CommunicationService.applyProviderStatus(...)
 * with the providerMessageId returned here.
 */
public interface CollectionMessageSender {

    Set<CommunicationChannel> supportedChannels();

    SendResult send(OutboundMessage message);

    record OutboundMessage(TenantId tenantId, UUID communicationId, CommunicationChannel channel, String to,
                           String subject, String body) {}

    record SendResult(boolean accepted, String providerMessageId, String error) {
        public static SendResult accepted(String providerMessageId) { return new SendResult(true, providerMessageId, null); }
        public static SendResult rejected(String error) { return new SendResult(false, null, error); }
    }
}
