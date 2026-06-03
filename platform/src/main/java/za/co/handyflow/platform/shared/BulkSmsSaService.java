package za.co.handyflow.platform.shared;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * BulkSMS.com provider — active when sms.enabled=true.
 *
 * BulkSMS is the dominant SA SMS provider: local coverage, competitive rates,
 * supports unicode, REST API. Sign up at bulksms.com.
 *
 * Required config (application.yaml or env vars):
 *   sms:
 *     enabled: true
 *     provider: bulksms
 *     bulksms:
 *       token-id: your-token-id
 *       token-secret: your-token-secret
 *       sender-id: HandyFlow          # optional, max 11 chars
 *
 * Alternative: SMSPortal (smsportal.com) — also large in SA.
 * To switch: implement SmsService, annotate with @ConditionalOnProperty(name="sms.provider",
 * havingValue="smsportal") and inject instead.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true")
public class BulkSmsSaService implements SmsService {

    private static final String API_URL = "https://api.bulksms.com/v1/messages";

    @Value("${sms.bulksms.token-id}")
    private String tokenId;

    @Value("${sms.bulksms.token-secret}")
    private String tokenSecret;

    @Value("${sms.bulksms.sender-id:HandyFlow}")
    private String senderId;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean send(String to, String message) {
        try {
            String e164 = normaliseToE164(to);
            if (e164 == null) {
                log.warn("SMS: invalid phone number format — skipping: {}", maskPhone(to));
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            // BulkSMS uses HTTP Basic Auth with token-id:token-secret
            String auth = Base64.getEncoder().encodeToString(
                    (tokenId + ":" + tokenSecret).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + auth);

            Map<String, Object> body = Map.of(
                    "to",   e164,
                    "body", message,
                    "from", senderId,
                    "encoding", "UNICODE"    // supports SA languages, Afrikaans chars
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (ok) {
                log.info("SMS sent to={} status={}", maskPhone(e164), response.getStatusCode());
            } else {
                log.warn("SMS provider rejected message to={} status={} body={}",
                        maskPhone(e164), response.getStatusCode(), response.getBody());
            }
            return ok;

        } catch (Exception e) {
            log.error("SMS send failed to={}: {}", maskPhone(to), e.getMessage());
            return false;
        }
    }

    /**
     * Converts SA mobile numbers to E.164 format (+27XXXXXXXXX).
     * Handles: 0821234567, +27821234567, 27821234567.
     * Returns null if the number is clearly invalid.
     */
    private String normaliseToE164(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+27") && digits.length() == 12) return digits;
        if (digits.startsWith("27")  && digits.length() == 11) return "+" + digits;
        if (digits.startsWith("0")   && digits.length() == 10) return "+27" + digits.substring(1);
        // International numbers already in E.164 starting with another country code
        if (digits.startsWith("+") && digits.length() >= 10) return digits;
        return null;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
