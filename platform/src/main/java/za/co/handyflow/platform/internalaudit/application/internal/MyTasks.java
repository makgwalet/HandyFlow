package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What one person has to do in internal audit: actions they own, findings they must answer,
 * review notes waiting for their answer, and answered notes waiting for them to clear. Pure; no database.
 */
public final class MyTasks {
    private MyTasks() {}

    public enum Kind { ACTION, FINDING, NOTE_ANSWER, NOTE_CLEAR }

    public record ActionIn(UUID id, UUID engagementId, String engagement, String finding, String description, UUID owner, LocalDate dueDate, String status) {}
    public record FindingIn(UUID id, UUID engagementId, String engagement, String title, String severity, UUID owner, LocalDate dueDate, String status) {}
    public record NoteIn(UUID id, UUID engagementId, String engagement, String workpaper, String note, UUID raisedBy, UUID workpaperPreparedBy, String status) {}

    public record Task(Kind kind, UUID id, UUID engagementId, String title, String context, String severity, LocalDate dueDate, boolean overdue, long daysOverdue) {}
    public record Summary(int total, int overdue, int dueThisWeek) {}
    public record Result(Summary summary, List<Task> tasks) {}

    public static Result build(UUID me, List<ActionIn> actions, List<FindingIn> findings, List<NoteIn> notes, LocalDate today) {
        List<Task> out = new ArrayList<>();
        if (me != null) {
            for (ActionIn a : actions) {
                if (!me.equals(a.owner()) || !"OPEN".equals(a.status())) continue;
                out.add(task(Kind.ACTION, a.id(), a.engagementId(), a.description(), join(a.finding(), a.engagement()), null, a.dueDate(), today));
            }
            for (FindingIn f : findings) {
                if (!me.equals(f.owner()) || !("OPEN".equals(f.status()) || "IN_PROGRESS".equals(f.status()))) continue;
                out.add(task(Kind.FINDING, f.id(), f.engagementId(), f.title(), f.engagement(), f.severity(), f.dueDate(), today));
            }
            for (NoteIn n : notes) {
                if ("OPEN".equals(n.status()) && me.equals(n.workpaperPreparedBy()))
                    out.add(task(Kind.NOTE_ANSWER, n.id(), n.engagementId(), n.note(), join(n.workpaper(), n.engagement()), null, null, today));
                else if ("RESPONDED".equals(n.status()) && me.equals(n.raisedBy()))
                    out.add(task(Kind.NOTE_CLEAR, n.id(), n.engagementId(), n.note(), join(n.workpaper(), n.engagement()), null, null, today));
            }
        }
        out.sort(Comparator.comparing(Task::overdue).reversed()
                .thenComparing(Comparator.comparingLong(Task::daysOverdue).reversed())
                .thenComparing(Task::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::title, Comparator.nullsLast(Comparator.naturalOrder())));
        int overdue = (int) out.stream().filter(Task::overdue).count();
        int soon = (int) out.stream().filter(t -> !t.overdue() && t.dueDate() != null && !t.dueDate().isAfter(today.plusDays(7))).count();
        return new Result(new Summary(out.size(), overdue, soon), out);
    }

    private static Task task(Kind kind, UUID id, UUID engagementId, String title, String context, String severity, LocalDate due, LocalDate today) {
        boolean late = due != null && due.isBefore(today);
        return new Task(kind, id, engagementId, title == null ? "" : title.trim(), context, severity, due, late, late ? ChronoUnit.DAYS.between(due, today) : 0);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " · " + b;
    }
}
