package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * handyflow.debtcollection.sms.http.* — configures a generic "POST JSON to an SMS gateway" sender. It is only
 * active when url is set.
 * url:                 https endpoint (plain http is refused).
 * auth-header-name/value: e.g. Authorization / "Bearer ..." or "Basic ..." — keep the value in your secret store.
 * body-template:       JSON with {{to}}, {{body}}, {{reference}} placeholders INSIDE string quotes,
 *                      e.g. {"to":"{{to}}","body":"{{body}}","reference":"{{reference}}"}
 * to-format:           E164_PLUS (+27821234567, default) | DIGITS (27821234567) | LOCAL (0821234567)
 * message-id-path:     where the provider returns its message id in the JSON response, e.g. "messages.0.id" (optional;
 *                      without it, delivery callbacks cannot be matched to messages)
 * success-status-min/max: HTTP status range counted as accepted (default 200–299).
 */
@Component
@ConfigurationProperties(prefix = "handyflow.debtcollection.sms.http")
@Getter
@Setter
public class SmsHttpProperties {
    private String url;
    private String authHeaderName = "Authorization";
    private String authHeaderValue;
    private String bodyTemplate = "{\"to\":\"{{to}}\",\"body\":\"{{body}}\",\"reference\":\"{{reference}}\"}";
    private String toFormat = "E164_PLUS";
    private String messageIdPath;
    private int successStatusMin = 200;
    private int successStatusMax = 299;
    private int timeoutSeconds = 15;
}
