package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * SMS adapter for any gateway that accepts a JSON POST — configured, not hard-coded (see SmsHttpProperties). Only
 * created when handyflow.debtcollection.sms.http.url is set; with it unset, SMS sends are recorded FAILED with
 * "No sender configured", never faked.
 * <p>
 * Safety: https only; every substituted value is JSON-escaped (JsonTemplate) so message text cannot alter the
 * request; the gateway's response body is never logged (it may echo the number/message). If message-id-path is
 * configured and found, the id is stored so delivery callbacks can be matched; otherwise the message ends at SENT.
 * This was written without a specific provider to test against — verify one real send in a sandbox before enabling.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "handyflow.debtcollection.sms.http", name = "url")
public class HttpSmsSender implements CollectionMessageSender {

    private final SmsHttpProperties props;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpSmsSender(SmsHttpProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds())).build();
    }

    @Override
    public Set<CommunicationChannel> supportedChannels() {
        return Set.of(CommunicationChannel.SMS);
    }

    @Override
    public SendResult send(OutboundMessage m) {
        try {
            URI uri = URI.create(props.getUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return SendResult.rejected("SMS gateway URL must be https");
            }
            String json = JsonTemplate.render(props.getBodyTemplate(), Map.of(
                    "to", formatNumber(m.to(), props.getToFormat()),
                    "body", m.body(),
                    "reference", m.communicationId().toString()));
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (props.getAuthHeaderValue() != null && !props.getAuthHeaderValue().isBlank()) {
                rb.header(props.getAuthHeaderName(), props.getAuthHeaderValue());
            }
            HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status < props.getSuccessStatusMin() || status > props.getSuccessStatusMax()) {
                return SendResult.rejected("SMS gateway returned HTTP " + status);
            }
            String id = null;
            if (props.getMessageIdPath() != null && !props.getMessageIdPath().isBlank()) {
                try {
                    JsonNode body = mapper.readTree(resp.body());
                    id = JsonPaths.text(body, props.getMessageIdPath());
                } catch (Exception ignored) {
                    // The message WAS accepted; a missing id only means no delivery callbacks for it.
                }
                if (id == null) log.warn("[Debt Collection] SMS accepted but no id found at '{}' for communication {}",
                        props.getMessageIdPath(), m.communicationId());
            }
            return SendResult.accepted(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.rejected("SMS send interrupted");
        } catch (Exception e) {
            log.warn("[Debt Collection] SMS send failed for communication {}: {}", m.communicationId(), e.getMessage());
            return SendResult.rejected("SMS failed: " + e.getMessage());
        }
    }

    static String formatNumber(String raw, String format) {
        String digits = ContactNormalizer.normalize(raw);
        return switch (format == null ? "E164_PLUS" : format.toUpperCase()) {
            case "DIGITS" -> digits;
            case "LOCAL" -> digits.startsWith("27") ? "0" + digits.substring(2) : digits;
            default -> "+" + digits;
        };
    }
}
