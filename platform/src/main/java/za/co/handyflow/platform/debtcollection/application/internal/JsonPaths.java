package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.JsonNode;

/** Minimal dotted-path lookup into a JSON tree: "messages.0.id" (digits index arrays). Returns null if any step is missing. */
final class JsonPaths {

    private JsonPaths() {}

    static String text(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        JsonNode cur = root;
        for (String part : path.split("\\.")) {
            if (cur == null) return null;
            cur = part.matches("\\d+") ? cur.get(Integer.parseInt(part)) : cur.get(part);
        }
        return cur == null || cur.isNull() || cur.isContainerNode() ? null : cur.asText();
    }
}
