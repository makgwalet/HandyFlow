package za.co.handyflow.platform.marketing.application.internal;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** A failed send, classified so the delivery engine knows what to do next. */
public class MailDeliveryException extends Exception {

    private final MarketingMailProvider.Kind kind;

    public MailDeliveryException(MarketingMailProvider.Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public MarketingMailProvider.Kind getKind() { return kind; }

    // Recipient is bad: enhanced status 5.1.x (bad destination), unknown user, etc.
    private static final Pattern RECIPIENT = Pattern.compile(
            "(?i)(\\b5\\.1\\.\\d+\\b|invalid addresses|user unknown|unknown user|no such user"
                    + "|does not exist|mailbox unavailable|recipient address rejected)");

    // Provider-wide: auth, quota, rate limiting, relay denied. Retrying later is right; blaming the recipient is wrong.
    private static final Pattern SYSTEM = Pattern.compile(
            "(?i)(authentication|username and password not accepted|\\b53[0458]\\b|\\b5\\.7\\.8\\b"
                    + "|relay access denied|quota|rate limit|too many|throttl|\\b5\\.4\\.5\\b|\\b4\\.7\\.\\d+\\b)");

    // Any other permanent SMTP failure: a 5xx reply with an enhanced status code.
    private static final Pattern PERMANENT = Pattern.compile("\\b5\\d{2}[ -]\\d\\.\\d{1,3}\\.\\d{1,3}\\b");

    /**
     * Classifies by inspecting every message in the cause chain. This is heuristic text
     * matching (the SMTP libraries don't expose one stable type for all of this), so
     * unknown failures default to TRANSIENT — retried a bounded number of times, never suppressed.
     */
    public static MailDeliveryException from(Throwable t) {
        Set<String> messages = new LinkedHashSet<>();
        for (Throwable c = t; c != null && messages.size() < 12; c = c.getCause()) {
            if (c.getMessage() != null) messages.add(c.getMessage());
            if (c instanceof org.springframework.mail.MailSendException mse) {
                mse.getMessageExceptions();
                if (mse.getFailedMessages() != null) {
                    mse.getFailedMessages().values().forEach(e -> {
                        if (e.getMessage() != null) messages.add(e.getMessage());
                    });
                }
            }
        }
        String text = String.join(" | ", messages);
        String shortText = text.length() > 240 ? text.substring(0, 240) : text;

        MarketingMailProvider.Kind kind;
        if (RECIPIENT.matcher(text).find())      kind = MarketingMailProvider.Kind.RECIPIENT_REJECTED;
        else if (SYSTEM.matcher(text).find())    kind = MarketingMailProvider.Kind.SYSTEM;
        else if (PERMANENT.matcher(text).find()) kind = MarketingMailProvider.Kind.PERMANENT;
        else                                     kind = MarketingMailProvider.Kind.TRANSIENT;

        return new MailDeliveryException(kind, shortText.isBlank() ? t.getClass().getSimpleName() : shortText, t);
    }
}