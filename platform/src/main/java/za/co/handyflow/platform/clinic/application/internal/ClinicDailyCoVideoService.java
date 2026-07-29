package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Creates video call rooms via Daily.co's REST API. Deliberately its own
 * small class — same shape as ClinicAiInterpretationService/EmailService/
 * FileStorageService in this codebase — so the "how do we reach the video
 * provider" concern stays separate from appointment domain logic.
 * <p>
 * WHY Daily.co specifically? No video infrastructure exists anywhere in
 * this platform yet (confirmed before building this). Building raw WebRTC
 * — signaling, STUN/TURN, ICE negotiation — from scratch is a project of
 * its own, not a feature to bolt on. Daily.co's hosted rooms are a single
 * REST call to create, and the returned room URL is itself a complete,
 * polished call UI (camera/mic controls, chat, screen share) — no custom
 * video code needed on either end. This is a deliberate vendor choice for
 * "telehealth in one session," not a judgment that it's the only option;
 * swapping providers later only touches this one file.
 * <p>
 * The API key only ever lives on the server, read from configuration
 * (daily.api-key — set via the DAILY_API_KEY environment variable or
 * application.yml, never committed to source) — same key-handling
 * discipline as the Anthropic integration.
 */
@Slf4j
@Component
public class ClinicDailyCoVideoService {

    @Value("${daily.api-key:}")
    private String apiKey;

    /** How long after creation an unused room expires, in hours. */
    private static final long ROOM_EXPIRY_HOURS = 6;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.daily.co/v1/rooms")
            .build();

    /**
     * @return the room's join URL, or null if the API key isn't configured
     * or the call fails — callers should treat a null result as "video
     * unavailable right now," not as an error that should block booking or
     * viewing the appointment itself.
     */
    @SuppressWarnings("unchecked")
    public String createRoom(String roomNamePrefix) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Daily.co API key not configured (daily.api-key) — video room not created");
            return null;
        }
        try {
            long expiresAt = Instant.now().plus(ROOM_EXPIRY_HOURS, ChronoUnit.HOURS).getEpochSecond();
            // Room names must be short/URL-safe — Daily.co generates one if
            // omitted, which is simpler and avoids collision handling here.
            Map<String, Object> body = Map.of(
                    "properties", Map.of(
                            "enable_chat", true,
                            "enable_screenshare", true,
                            "exp", expiresAt
                    )
            );

            Map<String, Object> response = restClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return null;
            return (String) response.get("url");
        } catch (Exception e) {
            log.error("Daily.co room creation failed: {}", e.getMessage(), e);
            return null;
        }
    }
}