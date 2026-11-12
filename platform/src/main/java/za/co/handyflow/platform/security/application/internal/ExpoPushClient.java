package za.co.handyflow.platform.security.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sends push messages through Expo's push service and reports, per message, whether the token is still good. */
@Slf4j
@Component
public class ExpoPushClient {

    public record Message(String to, String title, String body, Map<String, String> data) {}

    /** ok = accepted by Expo. deadToken = Expo says the phone no longer has the app (DeviceNotRegistered). */
    public record Result(boolean ok, boolean deadToken, String error) {}

    private static final int CHUNK = 100;

    private final RestClient http;
    private final ObjectMapper json;
    private final String accessToken;

    public ExpoPushClient(ObjectMapper json,
                          @Value("${handyflow.guard.push.expo-url:https://exp.host/--/api/v2/push/send}") String url,
                          @Value("${handyflow.guard.push.expo-access-token:}") String accessToken) {
        this.json = json;
        this.accessToken = accessToken;
        this.http = RestClient.builder().baseUrl(url).build();
    }

    public List<Result> send(List<Message> messages) {
        List<Result> out = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += CHUNK) {
            List<Message> chunk = messages.subList(from, Math.min(from + CHUNK, messages.size()));
            out.addAll(sendChunk(chunk));
        }
        return out;
    }

    private List<Result> sendChunk(List<Message> chunk) {
        List<Map<String, Object>> body = chunk.stream().map(m -> Map.<String, Object>of(
                "to", m.to(), "title", m.title(), "body", m.body(), "data", m.data(), "sound", "default", "channelId", "default")).toList();
        try {
            var req = http.post().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
            if (!accessToken.isBlank()) req = req.header("Authorization", "Bearer " + accessToken);
            String raw = req.body(body).retrieve().body(String.class);
            JsonNode data = json.readTree(raw).path("data");
            List<Result> results = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                JsonNode r = data.path(i);
                boolean ok = "ok".equals(r.path("status").asText());
                String error = r.path("details").path("error").asText(r.path("message").asText(""));
                results.add(new Result(ok, "DeviceNotRegistered".equals(error), ok ? null : error));
            }
            return results;
        } catch (Exception e) {
            log.warn("[Security] Push send failed for {} message(s): {}", chunk.size(), e.toString());
            return chunk.stream().map(m -> new Result(false, false, "SEND_FAILED")).toList();
        }
    }
}
