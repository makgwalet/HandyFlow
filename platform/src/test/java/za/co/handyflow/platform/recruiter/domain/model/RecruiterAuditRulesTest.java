package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.api.RecruiterCalendarController;
import za.co.handyflow.platform.recruiter.api.RecruiterClientController;
import za.co.handyflow.platform.recruiter.api.RecruiterCvMigrationController;
import za.co.handyflow.platform.recruiter.api.RecruiterController;
import za.co.handyflow.platform.recruiter.api.RecruiterDashboardController;
import za.co.handyflow.platform.recruiter.api.RecruiterPoolController;
import za.co.handyflow.platform.recruiter.api.RecruiterReportController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterAuditRulesTest {

    @Test
    void knownRoutesHaveNames() {
        assertThat(RecruiterAuditRules.actionFor("post", "/applications/{id}/stage")).isEqualTo("STAGE_MOVED");
        assertThat(RecruiterAuditRules.actionFor("GET", "/applications/{id}/stage")).isNull();
        assertThat(RecruiterAuditRules.actionFor("GET", "/jobs")).isNull();
        assertThat(RecruiterAuditRules.actionFor(null, "/jobs")).isNull();
        assertThat(RecruiterAuditRules.actionFor("DELETE", "/retention/remove")).isNull();
    }

    @Test
    void entityKinds() {
        assertThat(RecruiterAuditRules.entityType("/applications/{id}/stage")).isEqualTo("APPLICATION");
        assertThat(RecruiterAuditRules.entityType("/jobs/{id}")).isEqualTo("JOB");
        assertThat(RecruiterAuditRules.entityType("/pools")).isEqualTo("POOL");
        assertThat(RecruiterAuditRules.entityType("/candidates/{applicantId}/tags")).isEqualTo("CANDIDATE");
        assertThat(RecruiterAuditRules.entityType("/retention/remove")).isEqualTo("CANDIDATE");
        assertThat(RecruiterAuditRules.entityType("/settings")).isEqualTo("SETTINGS");
    }

    @Test
    void detailIsCleanedAndCut() {
        assertThat(RecruiterAuditRules.detail(null)).isNull();
        assertThat(RecruiterAuditRules.detail("  \n\t ")).isNull();
        assertThat(RecruiterAuditRules.detail("action=PUBLISH\r\nforged: line")).isEqualTo("action=PUBLISH forged: line");
        assertThat(RecruiterAuditRules.detail("x".repeat(600))).hasSize(500);
    }

    @Test
    void filterAndStatus() {
        assertThat(RecruiterAuditRules.filterProblem(null)).isNull();
        assertThat(RecruiterAuditRules.filterProblem(" ")).isNull();
        assertThat(RecruiterAuditRules.filterProblem("stage_moved")).isNull();
        assertThat(RecruiterAuditRules.filterProblem("DROP TABLE")).contains("not an action");
        assertThat(RecruiterAuditRules.isSuccess(200)).isTrue();
        assertThat(RecruiterAuditRules.isSuccess(201)).isTrue();
        assertThat(RecruiterAuditRules.isSuccess(302)).isFalse();
        assertThat(RecruiterAuditRules.isSuccess(403)).isFalse();
    }

    /** Every staff endpoint that changes data must be in the table, so adding one without auditing it fails here. */
    @Test
    void everyStaffEndpointThatChangesDataIsAudited() {
        Set<String> exempt = Set.of("POST /careers/{tenantSlug}/jobs/{jobId}/apply", "PUT /portal/{token}/profile",
                "POST /portal/{token}/applications/{applicationId}/withdraw");
        List<String> missing = new ArrayList<>();
        for (Class<?> c : List.of(RecruiterController.class, RecruiterPoolController.class, RecruiterReportController.class, RecruiterClientController.class, RecruiterCvMigrationController.class, RecruiterCalendarController.class, RecruiterDashboardController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0].replace("/api/v1/recruiter", "");
            for (Method m : c.getDeclaredMethods()) {
                String verb = null; String path = "";
                if (m.isAnnotationPresent(PostMapping.class)) { verb = "POST"; path = first(m.getAnnotation(PostMapping.class).value()); }
                if (m.isAnnotationPresent(PutMapping.class)) { verb = "PUT"; path = first(m.getAnnotation(PutMapping.class).value()); }
                if (m.isAnnotationPresent(DeleteMapping.class)) { verb = "DELETE"; path = first(m.getAnnotation(DeleteMapping.class).value()); }
                if (verb == null) continue;
                String key = verb + " " + base + path;
                if (!exempt.contains(key) && RecruiterAuditRules.actionFor(verb, base + path) == null) missing.add(key);
            }
        }
        assertThat(missing).isEmpty();
    }

    private static String first(String[] v) { return v.length == 0 ? "" : v[0]; }
}
