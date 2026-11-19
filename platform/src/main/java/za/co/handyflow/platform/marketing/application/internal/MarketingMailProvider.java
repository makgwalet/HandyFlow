package za.co.handyflow.platform.marketing.application.internal;

import java.util.UUID;

/**
 * Port for sending ONE marketing email synchronously. Unlike EmailService.send()
 * (fire-and-forget, swallows exceptions) this returns a real result or throws a
 * classified {@link MailDeliveryException}, which the delivery engine needs in
 * order to retry, bounce or back off correctly.
 *
 * To add SES / SendGrid / Mailgun API sending later, implement this interface and
 * select it with marketing.mail.provider.
 */
public interface MarketingMailProvider {

    String name();

    SendResult send(OutboundMessage message) throws MailDeliveryException;

    /** What went wrong, from the delivery engine's point of view. */
    enum Kind {
        /** Temporary (4xx, network, timeout): retry with back-off. */
        TRANSIENT,
        /** Provider-wide problem (auth, quota, rate limit): retry later WITHOUT using up the item's retries. */
        SYSTEM,
        /** Permanent, not recipient-specific: give up on this item, do not suppress. */
        PERMANENT,
        /** The recipient address itself was rejected: a hard bounce, so suppress. */
        RECIPIENT_REJECTED
    }

    record OutboundMessage(
            UUID    campaignContactId,
            String  to,
            String  toName,
            String  subject,
            String  html,
            String  fromName,
            String  replyTo,
            String  unsubscribeUrl) {}

    /** providerMessageId may be null if the provider gave none. */
    record SendResult(String providerMessageId) {}
}