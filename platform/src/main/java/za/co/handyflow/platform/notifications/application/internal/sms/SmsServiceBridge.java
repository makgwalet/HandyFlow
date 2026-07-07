package za.co.handyflow.platform.notifications.application.internal.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SmsService;


/**
 * *** THIS CLASS REPLACES LoggingSmsSender — DELETE THAT FILE ***
 * <p>
 * WHY: two independent SMS abstractions existed in this codebase, built at
 * different times for different call sites without either knowing the other
 * existed — {@code SmsSender} here (used by {@link SmsChannelSender}, backing
 * Earthmoving/Fleet's critical breakdown/compliance alerts) and
 * {@code za.co.handyflow.platform.shared.SmsService} (used by
 * {@code ContractingService}, backing OTP delivery, already correctly wired
 * to real BulkSMS via a single {@code sms.enabled} flag). Setting
 * {@code sms.enabled=true} lit up OTP delivery but did nothing for
 * Earthmoving/Fleet — {@code LoggingSmsSender} kept being the only active
 * {@code SmsSender} bean regardless, since nothing connected the two.
 * <p>
 * FIX: rather than add a second, parallel "which provider" config axis on
 * top of the one that already works correctly ({@code sms.enabled} /
 * {@code sms.provider} in application.yaml, already resolving to either a
 * safe stub or real BulkSMS), this class makes {@code SmsSender} simply
 * delegate to whichever {@code SmsService} bean Spring already wired up.
 * That means {@code sms.enabled=true} now correctly lights up BOTH OTP
 * delivery AND every notification-pipeline SMS (Earthmoving breakdowns,
 * Fleet driver PrDP alerts, etc.) from the one flag, as anyone configuring
 * this would reasonably expect. {@code LoggingSmsSender} and the
 * {@code notifications.sms.provider} config key it read are now fully
 * redundant — {@code StubSmsService} (the {@code SmsService} implementation
 * active when {@code sms.enabled=false}) already covers the exact same
 * "safe no-op that logs" role, so nothing is lost by removing them.
 * <p>
 * ACTION NEEDED ON YOUR SIDE:
 * <ol>
 *   <li>Delete {@code LoggingSmsSender.java} — this class is its replacement,
 *       and Spring will fail to start with an ambiguous-bean error if both
 *       exist and both claim to be the app's {@code SmsSender}.</li>
 *   <li>Remove the now-unused {@code notifications.sms.provider} key from
 *       application.yaml — {@code sms.enabled}/{@code sms.provider} in the
 *       same file are the only SMS config that does anything now.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsServiceBridge implements za.co.handyflow.platform.notifications.application.internal.sms.SmsSender {

    private final SmsService smsService;

    @Override
    public void send(String toE164, String message) {
        try {
            boolean sent = smsService.send(toE164, message);
            if (!sent) {
                log.warn("Notification SMS not delivered to={} — SmsService reported failure "
                        + "(check provider config/logs for the actual reason)", toE164);
            }
        } catch (Exception e) {
            // Same principle as ContractingService.safeSms(): a flaky SMS
            // provider must never be allowed to propagate an exception back
            // into whatever called this — SmsChannelSender already runs this
            // asynchronously and in isolation from the business action that
            // triggered the notification, but staying defensive here too
            // means this class is safe even if it's ever called from
            // somewhere less isolated in the future.
            log.error("SMS send failed to={} (notification dispatch continues regardless): {}",
                    toE164, e.getMessage(), e);
        }
    }
}