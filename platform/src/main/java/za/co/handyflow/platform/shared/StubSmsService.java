package za.co.handyflow.platform.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development SMS stub — active when sms.enabled=false.
 *
 * Logs the OTP message to the console so you can complete signing flows during
 * development without a real SMS provider account.
 *
 * The OtpDevController (also gated on sms.enabled=false) provides the OTP via HTTP.
 * This stub gives you the OTP in the application log as an additional fallback.
 *
 * NEVER enable in production — sms.enabled must be true in production config.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.enabled", havingValue = "false", matchIfMissing = false)
public class StubSmsService implements SmsService {

    @Override
    public boolean send(String to, String message) {
        // Mask the phone number in the log but show the full OTP for dev convenience
        String masked = to != null && to.length() >= 4
                ? "***" + to.substring(to.length() - 4)
                : "***";
        log.info("=======================================================");
        log.info("DEV SMS (not sent) to={}", masked);
        log.info("MSG: {}", message);
        log.info("=======================================================");
        return true;
    }
}
