package za.co.handyflow.platform.security.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/** Reads the scope column ("[\"/api/v1/security/reports\"]"). Blank is "no scope" (empty list); text that is not a JSON array of strings is malformed (empty Optional): fail closed. */
public final class ApiKeyScopeJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiKeyScopeJson() {}

    public static Optional<List<String>> parse(String json) {
        if (json == null || json.isBlank()) return Optional.of(List.of());
        try {
            return Optional.of(MAPPER.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
