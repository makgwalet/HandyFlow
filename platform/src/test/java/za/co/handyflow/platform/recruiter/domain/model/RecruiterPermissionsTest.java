package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import za.co.handyflow.platform.recruiter.api.RecruiterAuditController;
import za.co.handyflow.platform.recruiter.api.RecruiterCalendarController;
import za.co.handyflow.platform.recruiter.api.RecruiterClientController;
import za.co.handyflow.platform.recruiter.api.RecruiterCvMigrationController;
import za.co.handyflow.platform.recruiter.api.RecruiterController;
import za.co.handyflow.platform.recruiter.api.RecruiterDashboardController;
import za.co.handyflow.platform.recruiter.api.RecruiterPoolController;
import za.co.handyflow.platform.recruiter.api.RecruiterReportController;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterPermissionsTest {
    private static final Pattern AUTH = Pattern.compile("hasAuthority\\('([A-Z_]+)'\\)");

    @Test
    void movingToHiredNeedsTheHirePermission() {
        assertThat(RecruiterPermissions.stageProblem("HIRED", false)).contains("hired");
        assertThat(RecruiterPermissions.stageProblem(" hired ", false)).isNotNull();
        assertThat(RecruiterPermissions.stageProblem("HIRED", true)).isNull();
        assertThat(RecruiterPermissions.stageProblem("OFFER", false)).isNull();
        assertThat(RecruiterPermissions.stageProblem(null, false)).isNull();
    }

    @Test
    void everyEndpointAsksForAPermissionThatExists() {
        for (Class<?> c : new Class<?>[]{RecruiterController.class, RecruiterPoolController.class, RecruiterReportController.class, RecruiterAuditController.class, RecruiterDashboardController.class, RecruiterCalendarController.class, RecruiterClientController.class, RecruiterCvMigrationController.class}) {
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) continue;
                Matcher mt = AUTH.matcher(pa.value());
                int found = 0;
                while (mt.find()) { found++; assertThat(RecruiterPermissions.ALL).as(c.getSimpleName() + "." + m.getName()).contains(mt.group(1)); }
                assertThat(found).as(c.getSimpleName() + "." + m.getName()).isGreaterThan(0);
            }
        }
    }

    @Test
    void theSensitiveActionsAskForTheirOwnPermission() throws Exception {
        assertThat(need(RecruiterController.class, "viewCv")).isEqualTo("RECRUITER_CV");
        assertThat(need(RecruiterController.class, "downloadScorecard")).isEqualTo("RECRUITER_CV");
        assertThat(need(RecruiterController.class, "sendOfferLetter")).isEqualTo("RECRUITER_OFFER");
        assertThat(need(RecruiterController.class, "outbox")).isEqualTo("RECRUITER_AUDIT");
        assertThat(need(RecruiterController.class, "convertToEmployee")).contains("RECRUITER_ADMIN").contains("RECRUITER_HIRE");
        assertThat(need(RecruiterReportController.class, "export")).isEqualTo("RECRUITER_REPORTS");
    }

    private static String need(Class<?> c, String method) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(method) && m.getAnnotation(PreAuthorize.class) != null) {
                Matcher mt = AUTH.matcher(m.getAnnotation(PreAuthorize.class).value());
                StringBuilder sb = new StringBuilder();
                while (mt.find()) sb.append(sb.length() == 0 ? "" : " ").append(mt.group(1));
                return sb.toString();
            }
        }
        throw new AssertionError("no such method " + c.getSimpleName() + "." + method);
    }
}
