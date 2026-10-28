package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.training.api.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingAuditRulesTest {

    @Test
    void knownRoutesHaveActionNamesAndOthersDoNot() {
        assertThat(TrainingAuditRules.actionFor("POST", "/enrollments/{id}/complete")).isEqualTo("RESULT_RECORDED");
        assertThat(TrainingAuditRules.actionFor("post", "/certificates/{id}/revoke")).isEqualTo("CERTIFICATE_REVOKED");
        assertThat(TrainingAuditRules.actionFor("GET", "/courses")).isNull();
        assertThat(TrainingAuditRules.actionFor(null, "/courses")).isNull();
    }

    @Test
    void entityTypeFollowsTheRoute() {
        assertThat(TrainingAuditRules.entityType("/courses/{id}/archive")).isEqualTo("COURSE");
        assertThat(TrainingAuditRules.entityType("/sessions/{sessionId}/enrollments")).isEqualTo("SESSION");
        assertThat(TrainingAuditRules.entityType("/mine/bookings")).isEqualTo("ENROLMENT");
        assertThat(TrainingAuditRules.entityType("/audit/export.csv")).isEqualTo("SETTINGS");
    }

    @Test
    void detailIsCleanedAndCut() {
        assertThat(TrainingAuditRules.detail(null)).isNull();
        assertThat(TrainingAuditRules.detail("  \n\t ")).isNull();
        assertThat(TrainingAuditRules.detail("a\n\tb   c")).isEqualTo("a b c");
        assertThat(TrainingAuditRules.detail("x".repeat(900))).hasSize(TrainingAuditRules.MAX_DETAIL);
    }

    @Test
    void filtersAndStatuses() {
        assertThat(TrainingAuditRules.filterProblem(null)).isNull();
        assertThat(TrainingAuditRules.filterProblem(" ")).isNull();
        assertThat(TrainingAuditRules.filterProblem("certificate_issued")).isNull();
        assertThat(TrainingAuditRules.filterProblem("DROP TABLE")).contains("not an action");
        assertThat(TrainingAuditRules.isSuccess(204)).isTrue();
        assertThat(TrainingAuditRules.isSuccess(403)).isFalse();
    }

    @Test
    void rangeRules() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        assertThat(TrainingAuditRules.rangeProblem(null, null, today)).isNull();
        assertThat(TrainingAuditRules.rangeProblem(today.minusDays(10), today, today)).isNull();
        assertThat(TrainingAuditRules.rangeProblem(today, today.minusDays(1), today)).contains("after");
        assertThat(TrainingAuditRules.rangeProblem(null, today.plusDays(1), today)).contains("future");
        assertThat(TrainingAuditRules.rangeProblem(today.minusDays(400), today, today)).contains("366");
        assertThat(TrainingAuditRules.fromOrDefault(null, null, today)).isEqualTo(today.minusDays(90));
    }

    @Test
    void csvQuotesAndNeutralisesFormulas() {
        String csv = TrainingAuditRules.csv(List.of("A", "B"), List.of(List.of("=1+1", "has, comma"), List.of("-5", "say \"hi\"")));
        assertThat(csv).contains("'=1+1").contains("\"has, comma\"").contains("-5,").contains("\"say \"\"hi\"\"\"");
    }

    /** Every Training endpoint that changes data must be in the table, so adding one without auditing it fails here. */
    @Test
    void everyEndpointThatChangesDataIsAudited() {
        List<String> missing = new ArrayList<>();
        for (Class<?> c : List.of(TrainingCourseController.class, TrainingSessionController.class, TrainingEnrollmentController.class, TrainingCertificateController.class,
                TrainingRequirementController.class, TrainingSelfController.class, TrainingTeamController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0].replace("/api/v1/training", "");
            for (Method m : c.getDeclaredMethods()) {
                String verb = null; String path = "";
                if (m.isAnnotationPresent(PostMapping.class)) { verb = "POST"; path = first(m.getAnnotation(PostMapping.class).value()); }
                if (m.isAnnotationPresent(PutMapping.class)) { verb = "PUT"; path = first(m.getAnnotation(PutMapping.class).value()); }
                if (m.isAnnotationPresent(DeleteMapping.class)) { verb = "DELETE"; path = first(m.getAnnotation(DeleteMapping.class).value()); }
                if (verb == null) continue;
                if (TrainingAuditRules.actionFor(verb, base + path) == null) missing.add(verb + " " + base + path);
            }
        }
        assertThat(missing).isEmpty();
    }

    /** Every authority a Training endpoint asks for is one of the known ones, so a typo cannot lock everyone out. */
    @Test
    void everyAuthorityNamedByAnEndpointIsKnown() {
        Set<String> known = Set.of("TRAINING_READ", "TRAINING_MANAGE", "TRAINING_ADMIN", "TRAINING_AUDIT");
        List<String> bad = new ArrayList<>();
        for (Class<?> c : List.of(TrainingCourseController.class, TrainingSessionController.class, TrainingEnrollmentController.class, TrainingCertificateController.class,
                TrainingRequirementController.class, TrainingSelfController.class, TrainingTeamController.class, TrainingAuditController.class, TrainingSummaryController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                var pre = m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
                if (pre == null) continue;
                var matcher = java.util.regex.Pattern.compile("'(TRAINING_[A-Z_]+)'").matcher(pre.value());
                while (matcher.find()) if (!known.contains(matcher.group(1))) bad.add(c.getSimpleName() + "." + m.getName() + " " + matcher.group(1));
            }
        }
        assertThat(bad).isEmpty();
    }

    private static String first(String[] v) { return v.length == 0 ? "" : v[0]; }
}
