package za.co.handyflow.platform.projects.application.internal.notify;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Who hears about a project event, and when overdue reminders go out. */
class ProjectNotificationRulesTest {

    UUID tenant = UUID.randomUUID(), project = UUID.randomUUID();
    UUID manager = UUID.randomUUID(), sponsor = UUID.randomUUID(), worker = UUID.randomUUID();

    List<ProjectMember> members() {
        return List.of(
                ProjectMember.create(tenant, project, manager, "Lerato", ProjectMemberRole.MANAGER, null),
                ProjectMember.create(tenant, project, sponsor, "Sipho", ProjectMemberRole.SPONSOR, null),
                ProjectMember.create(tenant, project, worker, "Thabo", ProjectMemberRole.MEMBER, null));
    }

    @Test
    void roleHoldersAndNamedPeopleAreNotifiedButNeverTheActor() {
        var e = new ProjectEvent(tenant, project, PmEvent.CHANGE_ORDER_SUBMITTED, "t", "m", null, Set.of(),
                Set.of(ProjectMemberRole.SPONSOR, ProjectMemberRole.MANAGER), manager);
        assertEquals(Set.of(sponsor), ProjectNotificationListener.recipients(e, members()), "the manager submitted it");

        var assigned = ProjectEvent.to(tenant, project, PmEvent.TASK_ASSIGNED, "t", "m", null, worker, manager);
        assertEquals(Set.of(worker), ProjectNotificationListener.recipients(assigned, members()));
        var self = ProjectEvent.to(tenant, project, PmEvent.TASK_ASSIGNED, "t", "m", null, worker, worker);
        assertTrue(ProjectNotificationListener.recipients(self, members()).isEmpty(), "assigning yourself sends nothing");
    }

    @Test
    void overdueRemindersGoTheFirstDayThenWeekly() {
        LocalDate due = LocalDate.of(2026, 11, 1);
        assertFalse(ProjectReminderJob.remindToday(due, due), "due today is not overdue");
        assertTrue(ProjectReminderJob.remindToday(due, due.plusDays(1)));
        assertFalse(ProjectReminderJob.remindToday(due, due.plusDays(2)));
        assertTrue(ProjectReminderJob.remindToday(due, due.plusDays(8)));
        assertFalse(ProjectReminderJob.remindToday(due, due.plusDays(9)));
        assertTrue(ProjectReminderJob.remindToday(due, due.plusDays(15)));
    }

    @Test
    void everyEventHasAPlatformTypeAndALabel() {
        for (PmEvent e : PmEvent.values()) {
            assertNotNull(e.type(), e.name());
            assertFalse(e.label().isBlank(), e.name());
            assertTrue(e.type().defaultChannels().contains(za.co.handyflow.platform.notifications.domain.model.NotificationChannel.IN_APP));
        }
    }
}
