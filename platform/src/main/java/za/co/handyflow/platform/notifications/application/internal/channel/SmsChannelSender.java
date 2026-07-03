package za.co.handyflow.platform.notifications.application.internal.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.sms.SmsSender;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannelSender {

    private final SmsSender smsSender;
    private static final int SMS_MAX_LEN = 300; // ~2 SMS segments; keep provider costs sane

    @Async("notificationExecutor")
    public void send(NotificationRequest request, Recipient recipient) {
        if (recipient.phone() == null || recipient.phone().isBlank()) {
            log.warn("Skipping SMS for recipient={} on notification type={} — no phone number",
                    recipient.displayName(), request.type());
            return;
        }
        try {
            String body = request.title() + ": " + request.message();
            if (body.length() > SMS_MAX_LEN) {
                body = body.substring(0, SMS_MAX_LEN - 1) + "…";
            }
            smsSender.send(recipient.phone(), body);
        } catch (Exception e) {
            log.error("Failed to send notification SMS to={} type={}: {}",
                    recipient.phone(), request.type(), e.getMessage(), e);
        }
    }
}