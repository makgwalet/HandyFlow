package za.co.handyflow.platform.marketing.application.internal;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation over the existing JavaMailSender. Synchronous: it returns only
 * after the SMTP server has accepted (or refused) the message.
 *
 * "Accepted" is NOT "delivered". SMTP can't report delivery, so with plain SMTP
 * (e.g. Gmail) states stop at ACCEPTED; real DELIVERED / BOUNCED / COMPLAINED need a provider webhook.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "marketing.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMarketingMailProvider implements MarketingMailProvider {

    private final JavaMailSender mailSender;

    @Value("${notifications.email.from-address}")
    private String fromAddress;

    @Value("${notifications.email.from-name:HandyFlow}")
    private String defaultFromName;

    public SmtpMarketingMailProvider(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public String name() { return "smtp"; }

    @Override
    public SendResult send(OutboundMessage m) throws MailDeliveryException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromName = m.fromName() != null && !m.fromName().isBlank() ? m.fromName() : defaultFromName;
            helper.setFrom(fromAddress, fromName);

            try {
                helper.setTo(new InternetAddress(m.to(), true));
            } catch (AddressException e) {
                throw new MailDeliveryException(Kind.RECIPIENT_REJECTED, "Invalid address: " + m.to(), e);
            }
            if (m.replyTo() != null && !m.replyTo().isBlank()) {
                try { helper.setReplyTo(m.replyTo().trim()); }
                catch (Exception ignored) { /* an invalid reply-to must not block the send */ }
            }

            helper.setSubject(m.subject());
            helper.setText(m.html(), true);

            // One-click unsubscribe (RFC 8058) — also what Gmail/Yahoo expect from bulk senders.
            message.setHeader("List-Unsubscribe", "<" + m.unsubscribeUrl() + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            message.setHeader("Precedence", "bulk");

            // Correlation id so webhook events can be matched back to this exact send.
            String cc = m.campaignContactId().toString();
            message.setHeader("X-HF-Campaign-Contact", cc);
            message.setHeader("X-Mailgun-Variables", "{\"cc\":\"" + cc + "\"}");
            message.setHeader("X-PM-Metadata-cc", cc);

            mailSender.send(message);
            return new SendResult(message.getMessageID());

        } catch (MailDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw MailDeliveryException.from(e);
        }
    }
}