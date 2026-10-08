package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RestrictedListRedactorTest {
    private static final ObjectMapper M = new ObjectMapper();
    private final UUID secret = UUID.randomUUID(), open = UUID.randomUUID();

    private JsonNode body() throws Exception {
        return M.readTree("{\"success\":true,\"data\":[" +
            "{\"id\":\"r1\",\"patientId\":\"" + secret + "\",\"patientNameRaw\":\"Ann One\",\"status\":\"UNREVIEWED\",\"hasCritical\":true," +
            "\"criticalMarkers\":\"K 6.9\",\"receivedAt\":\"2026-10-08T06:00:00Z\",\"newFieldAddedLater\":\"secret\"}," +
            "{\"id\":\"r2\",\"patientId\":\"" + open + "\",\"patientNameRaw\":\"Bob Two\",\"criticalMarkers\":\"none\"}," +
            "{\"id\":\"t1\",\"patientId\":\"" + secret + "\",\"title\":\"Follow up result for Ann One\",\"detail\":\"HIV positive\",\"kind\":\"RESULT_FOLLOW_UP\",\"dueDate\":\"2026-10-09\"}," +
            "{\"id\":\"r3\",\"patientId\":null,\"patientNameRaw\":\"Unmatched\"}]}");
    }

    @Test void masksOnlyHiddenPatientsAndKeepsWhatIsNeededToAct() throws Exception {
        JsonNode b = body();
        assertEquals(2, RestrictedListRedactor.redact(b, Set.of(secret)));
        JsonNode r1 = b.get("data").get(0);
        assertEquals("Restricted record", r1.get("patientNameRaw").asText());
        assertTrue(r1.get("restricted").asBoolean());
        assertTrue(r1.get("hasCritical").asBoolean());          // a critical result must still read as critical
        assertEquals("UNREVIEWED", r1.get("status").asText());
        assertEquals("r1", r1.get("id").asText());
        assertFalse(r1.has("criticalMarkers"));
        assertFalse(r1.has("newFieldAddedLater"));              // anything not on the keep list is hidden by default
    }

    @Test void taskTitleAndDetailDoNotLeak() throws Exception {
        JsonNode t = body().get("data").get(2);
        RestrictedListRedactor.redact(t, Set.of(secret));
        assertEquals("Restricted record", t.get("title").asText());
        assertFalse(t.has("detail"));
        assertEquals("2026-10-09", t.get("dueDate").asText());
        assertFalse(t.toString().contains("Ann One")); assertFalse(t.toString().contains("HIV"));
    }

    @Test void otherRowsAreUntouched() throws Exception {
        JsonNode b = body();
        RestrictedListRedactor.redact(b, Set.of(secret));
        assertEquals("Bob Two", b.get("data").get(1).get("patientNameRaw").asText());
        assertEquals("none", b.get("data").get(1).get("criticalMarkers").asText());
        assertEquals("Unmatched", b.get("data").get(3).get("patientNameRaw").asText());
    }

    @Test void nothingHiddenMeansNothingChanged() throws Exception {
        JsonNode b = body(); String before = b.toString();
        assertEquals(0, RestrictedListRedactor.redact(b, Set.of()));
        assertEquals(before, b.toString());
    }

    @Test void findsEveryPatientIdIncludingNested() throws Exception {
        JsonNode nested = M.readTree("{\"data\":{\"content\":[{\"patientId\":\"" + secret + "\"}],\"other\":{\"patientId\":\"" + open + "\"}},\"x\":{\"patientId\":\"not-a-uuid\"}}");
        assertEquals(Set.of(secret, open), RestrictedListRedactor.patientIds(nested));
    }

    @Test void onlyTheNamedWorklistsAreMasked() {
        for (String p : new String[]{"/api/v1/clinic/lab/results", "/api/v1/clinic/lab/critical", "/api/v1/clinic/consultations/drafts",
                "/api/v1/clinic/consultations/handoff-queue", "/api/v1/clinic/recalls", "/api/v1/clinic/tasks", "/api/v1/clinic/tasks/"})
            assertTrue(RestrictedListRedactor.applies("GET", p), p);
        for (String p : new String[]{"/api/v1/clinic/patients", "/api/v1/clinic/appointments", "/api/v1/clinic/billing/claims",
                "/api/v1/clinic/break-glass/sessions", "/api/v1/clinic/access-log", "/api/v1/clinic/dashboard/summary", "/api/v1/clinic/lab/results/abc"})
            assertFalse(RestrictedListRedactor.applies("GET", p), p);
        assertFalse(RestrictedListRedactor.applies("POST", "/api/v1/clinic/tasks"));
    }
}
