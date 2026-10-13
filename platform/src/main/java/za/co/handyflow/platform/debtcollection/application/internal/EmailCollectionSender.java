package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.shared.EmailService;

import java.util.Set;

/**
 * EMAIL adapter over the platform's existing shared EmailService (the same one portal invites use).
 * The body is plain text turned into escaped HTML, so nothing a debtor-specific field contains can inject markup.
 * LIMITATION: EmailService.send() returns no provider message id, so a message sent this way ends at SENT — there is
 * no delivery callback to move it to DELIVERED/BOUNCED. Accepted means "handed to the platform's mailer without an
 * exception"; a mailer that fails asynchronously after that is invisible here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCollectionSender implements CollectionMessageSender {

    private final EmailService emailService;

    @Override
    public Set<CommunicationChannel> supportedChannels() {
        return Set.of(CommunicationChannel.EMAIL);
    }

    @Override
    public SendResult send(OutboundMessage m) {
        try {
            emailService.send(m.to(), m.subject(), EmailBodyHtml.toHtml(m.body()));
            return SendResult.accepted(null);
        } catch (Exception e) {
            log.warn("[Debt Collection] email send failed for communication {}: {}", m.communicationId(), e.getMessage());
            return SendResult.rejected("Email failed: " + e.getMessage());
        }
    }
}
