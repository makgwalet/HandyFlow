package za.co.handyflow.platform.shared;

/**
 * Pluggable SMS provider interface.
 *
 * Implementations:
 *   BulkSmsSaService  — BulkSMS.com (dominant in SA, REST API, supports Unicode)
 *   StubSmsService    — logs the OTP instead of sending; active when sms.enabled=false
 *
 * Configuration:
 *   sms.enabled=true       → BulkSmsSaService is the active bean
 *   sms.enabled=false      → StubSmsService is the active bean (logs OTP to console)
 *
 * Wire into ContractingService by injecting SmsService, replacing the TODO stubs.
 */
public interface SmsService {

    /**
     * Sends a plain-text SMS to the given phone number.
     *
     * @param to      E.164 format preferred (+27821234567), falls back to local (0821234567)
     * @param message Plain text, keep under 160 chars for single SMS unit
     * @return true if the provider accepted the message (delivery not guaranteed)
     */
    boolean send(String to, String message);
}
