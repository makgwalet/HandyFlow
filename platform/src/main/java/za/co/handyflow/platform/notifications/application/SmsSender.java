package za.co.handyflow.platform.notifications.application.internal.sms;

/**
 * Port for SMS delivery. Deliberately provider-agnostic — the rest of the
 * codebase depends only on this interface, never on a specific vendor SDK.
 * <p>
 * To go live with a real provider (Twilio, Clickatell, AWS SNS, etc.):
 *   1. Add the provider's SDK dependency.
 *   2. Implement this interface, e.g. {@code TwilioSmsSender}.
 *   3. Annotate it {@code @ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "twilio")}.
 *   4. Set {@code notifications.sms.provider=twilio} in application.yml.
 * No other class in the codebase needs to change — that's the point of
 * coding to an interface here.
 */
public interface SmsSender {

    /**
     * @param toE164  destination number in E.164 format, e.g. "+27821234567"
     * @param message SMS body. Callers should keep this under ~160 chars to
     *                avoid multi-part message billing, but this port does not
     *                enforce that — it's a per-provider/business concern.
     */
    void send(String toE164, String message);
}