package za.co.handyflow.platform.projects.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.model.ProjectActionItem;
import za.co.handyflow.platform.projects.domain.model.ProjectIssue;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Issue lifecycle, action items and the task review step. */
class IssuesAndReviewTest {

    UUID t = UUID.randomUUID(), p = UUID.randomUUID();

    ProjectIssue issue() { return ProjectIssue.raise(t, p, "ISS-001", "Glass late", null, "HIGH", null, LocalDate.now(), UUID.randomUUID(), "Thabo"); }

    @Test
    void anIssueIsResolvedWithAResolutionThenClosedAndCanBeReopened() {
        ProjectIssue i = issue();
        assertTrue(i.isOpen());
        i.start();
        assertEquals("IN_PROGRESS", i.getStatus());
        assertThrows(IllegalStateException.class, i::close, "close before resolve");
        assertThrows(IllegalArgumentException.class, () -> i.resolve("  ", "Lerato"));
        i.resolve("Second supplier delivered", "Lerato");
        assertEquals("RESOLVED", i.getStatus());
        assertNotNull(i.getResolvedAt());
        assertThrows(IllegalStateException.class, () -> i.resolve("again", "Lerato"));
        i.close();
        assertEquals("CLOSED", i.getStatus());
        i.reopen();
        assertEquals("OPEN", i.getStatus());
        assertNull(i.getResolution());
        assertNull(i.getClosedAt());
    }

    @Test
    void anIssueRefusesAnUnknownPriority() {
        assertThrows(IllegalArgumentException.class, () -> ProjectIssue.raise(t, p, "ISS-2", "x", null, "URGENT", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> issue().edit(null, null, "URGENT", null));
    }

    @Test
    void actionItemsAreDoneOnceAndOverdueOnlyWhileOpen() {
        ProjectActionItem a = ProjectActionItem.create(t, p, "ISSUE", UUID.randomUUID(), "Call supplier", null, null, LocalDate.now().minusDays(1), null, "L");
        assertTrue(a.isOverdue(LocalDate.now()));
        a.markDone("Lerato");
        assertFalse(a.isOverdue(LocalDate.now()));
        assertThrows(IllegalStateException.class, () -> a.markDone("Lerato"));
        a.reopen();
        assertTrue(a.isOpen());
        assertThrows(IllegalArgumentException.class, () -> ProjectActionItem.create(t, p, "TASK", UUID.randomUUID(), "x", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ProjectActionItem.create(t, p, "RISK", UUID.randomUUID(), " ", null, null, null, null, null));
    }

    ProjectTask task(String status) throws Exception {
        ProjectTask task = new ProjectTask();
        Field f = ProjectTask.class.getDeclaredField("status"); f.setAccessible(true); f.set(task, status);
        Field pr = ProjectTask.class.getDeclaredField("progressPct"); pr.setAccessible(true); pr.set(task, BigDecimal.ZERO);
        return task;
    }

    @Test
    void workGoesToReviewAndIsSentBackOrSignedOff() throws Exception {
        ProjectTask task = task("IN_PROGRESS");
        task.submitForReview();
        assertEquals("IN_REVIEW", task.getStatus());
        task.updateProgress(new BigDecimal("80"));
        assertEquals("IN_REVIEW", task.getStatus(), "progress under 100% keeps it in review");
        task.returnFromReview();
        assertEquals("IN_PROGRESS", task.getStatus());
        assertThrows(IllegalStateException.class, task::returnFromReview);
        task.submitForReview();
        task.complete();
        assertEquals("COMPLETED", task.getStatus());
        assertThrows(IllegalStateException.class, task::submitForReview, "finished work is not reviewed again");
    }
}
