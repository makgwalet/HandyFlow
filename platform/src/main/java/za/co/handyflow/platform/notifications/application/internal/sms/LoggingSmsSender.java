package za.co.handyflow.platform.notifications.application.internal.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link SmsSender}: logs the message instead of sending it.
 * <p>
 * This is what makes "SMS" safe to wire into the notification pipeline
 * today, before a commercial provider is chosen — the app runs correctly
 * end-to-end, nothing throws, nothing silently disappears (it's in the
 * logs), and swapping in a real provider later is a config change plus one
 * new class, not a refactor of any calling code.
 * <p>
 * {@code matchIfMissing = true} means this is the active bean whenever
 * {@code notifications.sms.provider} is unset — i.e. out of the box.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements za.co.handyflow.platform.notifications.application.internal.sms.SmsSender {

    @Override
    public void send(String toE164, String message) {
        log.info("[SMS-STUB] No SMS provider configured — would send to={} message=\"{}\"", toE164, message);
    }
}