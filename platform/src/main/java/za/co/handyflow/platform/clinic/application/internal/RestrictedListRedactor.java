package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Worklists and inboxes must not show the content of a restricted patient's record to someone who cannot open it
 * (CLINIC-DEC-008). A row keeps what is needed to act on it (that it exists, when, its status, that a result is critical)
 * and loses everything else, including the patient's name. It works by keeping a short list of fields, not by removing a
 * list of sensitive ones, so a field added to a response later is hidden by default. Pure.
 */
public final class RestrictedListRedactor {
    private RestrictedListRedactor() {}

    public static final String MASK = "Restricted record";

    /** Only these Clinic list endpoints are masked. Scheduling, the patient directory and money are open, as in the interceptor. */
    private static final Pattern MASKED = Pattern.compile("^/api/v1/clinic/(lab/results|lab/critical|consultations/drafts|consultations/handoff-queue|recalls|tasks)/?$");

    private static final Set<String> KEEP = Set.of("id", "patientId", "status", "kind", "createdAt", "receivedAt", "takenAt", "updatedAt",
            "dueDate", "overdue", "hasCritical", "hasAbnormal", "assignedTo", "sourceType", "sourceId", "consultationId",
            "practitionerId", "practitionerName", "scheduledAt", "restricted");
    private static final Set<String> NAME_FIELDS = Set.of("patientName", "patientNameRaw", "fullName", "title", "name");

    public static boolean applies(String method, String path) {
        return "GET".equalsIgnoreCase(method) && path != null && MASKED.matcher(path).matches();
    }

    /** Every patient id that appears in the response. */
    public static Set<UUID> patientIds(JsonNode root) {
        Set<UUID> out = new HashSet<>();
        collect(root, out);
        return out;
    }

    private static void collect(JsonNode n, Set<UUID> out) {
        if (n == null) return;
        if (n.isObject()) {
            JsonNode p = n.get("patientId");
            if (p != null && p.isTextual()) {
                try { out.add(UUID.fromString(p.asText())); } catch (IllegalArgumentException ignored) { /* not an id */ }
            }
            n.fields().forEachRemaining(e -> collect(e.getValue(), out));
        } else if (n.isArray()) {
            for (JsonNode c : n) collect(c, out);
        }
    }

    /** Masks, in place, every object whose patientId is hidden. Returns how many rows were masked. */
    public static int redact(JsonNode root, Set<UUID> hidden) {
        if (hidden.isEmpty()) return 0;
        return walk(root, hidden);
    }

    private static int walk(JsonNode n, Set<UUID> hidden) {
        int count = 0;
        if (n == null) return 0;
        if (n.isObject()) {
            ObjectNode o = (ObjectNode) n;
            JsonNode p = o.get("patientId");
            if (p != null && p.isTextual() && isHidden(p.asText(), hidden)) {
                List<String> named = new ArrayList<>();
                for (Iterator<String> it = o.fieldNames(); it.hasNext(); ) { String f = it.next(); if (NAME_FIELDS.contains(f)) named.add(f); }
                o.retain(KEEP);
                for (String f : named) o.put(f, MASK);
                o.put("restricted", true);
                return 1;
            }
            List<JsonNode> kids = new ArrayList<>();
            o.elements().forEachRemaining(kids::add);
            for (JsonNode c : kids) count += walk(c, hidden);
        } else if (n.isArray()) {
            for (JsonNode c : (ArrayNode) n) count += walk(c, hidden);
        }
        return count;
    }

    private static boolean isHidden(String id, Set<UUID> hidden) {
        try { return hidden.contains(UUID.fromString(id)); } catch (IllegalArgumentException e) { return false; }
    }
}
