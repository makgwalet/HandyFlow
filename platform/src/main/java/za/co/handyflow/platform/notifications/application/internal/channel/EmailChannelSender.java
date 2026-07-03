package za.co.handyflow.platform.notifications.application.internal.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.EmailService;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelSender {

    private final EmailService emailService;

    /**
     * {@code @Async} here (in addition to the {@code @Async} inside
     * EmailService.send itself) is deliberate belt-and-braces: this class is
     * the boundary the notification pipeline calls, and it must never be
     * allowed to block the caller regardless of what EmailService does
     * internally in the future.
     * <p>
     * Any exception here is caught and logged, never rethrown — a bounced
     * email must not affect SMS delivery to the same recipient, or delivery
     * to any other recipient in the same notification. Isolate failures per
     * recipient-per-channel; never let one bad email address take down a
     * whole notification fan-out.
     */
    @Async("notificationExecutor")
    public void send(NotificationRequest request, Recipient recipient) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            log.warn("Skipping EMAIL for recipient={} on notification type={} — no email address",
                    recipient.displayName(), request.type());
            return;
        }
        try {
            emailService.send(recipient.email(), request.title(), buildHtmlBody(request));
        } catch (Exception e) {
            log.error("Failed to send notification email to={} type={}: {}",
                    recipient.email(), request.type(), e.getMessage(), e);
        }
    }

    private String buildHtmlBody(NotificationRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("<h2>").append(escape(request.title())).append("</h2>");
        html.append("<p>").append(escape(request.message())).append("</p>");
        if (request.actionUrl() != null) {
            html.append("<p><a href=\"").append(request.actionUrl()).append("\">View details</a></p>");
        }
        return html.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}