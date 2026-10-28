package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * How far an engagement has got, worked out from what is recorded against it rather than from a stored percentage, so it cannot drift from the
 * work. Four phases, each 0 to 100, and a plain average of them for the engagement. Pure, so it can be tested without a database.
 */
public final class EngagementSummaryBuilder {
    private EngagementSummaryBuilder() {}

    public record PlanningIn(boolean objectives, boolean scope, boolean criteria, boolean materiality, boolean team) {}
    public record ReportIn(String status, LocalDate createdOn, LocalDate issuedOn) {}
    public record Inputs(String status, LocalDate startDate, LocalDate endDate, LocalDate createdOn, PlanningIn planning,
                         int workpapers, int workpapersSignedOff, int programmeSteps, int programmeStepsDone, int sampleItems, int sampleItemsTested,
                         ReportIn report, int findings, int openFindings, int findingsPastDue, int actions, int actionsValidated, int actionsOverdue,
                         int team) {}

    public record Phase(String key, String label, int pct, int done, int total, String basis) {}
    public record Milestone(String key, String label, LocalDate date, String status) {}   // DONE | CURRENT | PENDING | LATE
    public record Counts(int workpapers, int workpapersSignedOff, int findings, int openFindings, int findingsPastDue, int actions,
                         int actionsValidated, int actionsOverdue, int team) {}
    public record Summary(int overallPct, List<Phase> phases, List<Milestone> milestones, Counts counts, String reportStatus) {}

    static int pct(int part, int whole) { return whole <= 0 ? 0 : Math.min(100, Math.round(part * 100f / whole)); }

    public static Summary build(Inputs in, LocalDate today) {
        PlanningIn p = in.planning();
        int planningDone = (p.objectives() ? 1 : 0) + (p.scope() ? 1 : 0) + (p.criteria() ? 1 : 0) + (p.materiality() ? 1 : 0) + (p.team() ? 1 : 0);
        Phase planning = new Phase("planning", "Planning", pct(planningDone, 5), planningDone, 5, "objectives, scope, criteria, materiality and team set");
        Phase fieldwork = new Phase("fieldwork", "Fieldwork", pct(in.workpapersSignedOff(), in.workpapers()), in.workpapersSignedOff(), in.workpapers(),
                in.workpapers() == 0 ? "no workpapers yet" : "workpapers signed off");
        Phase testing;
        if (in.programmeSteps() > 0)
            testing = new Phase("testing", "Testing", pct(in.programmeStepsDone(), in.programmeSteps()), in.programmeStepsDone(), in.programmeSteps(), "programme steps done");
        else if (in.sampleItems() > 0)
            testing = new Phase("testing", "Testing", pct(in.sampleItemsTested(), in.sampleItems()), in.sampleItemsTested(), in.sampleItems(), "sampled items tested");
        else
            testing = new Phase("testing", "Testing", 0, 0, 0, "no programme or sample yet");
        String rs = in.report() == null ? null : in.report().status();
        int reportPct = rs == null ? 0 : switch (rs) { case "DRAFT" -> 25; case "FACTUAL_REVIEW" -> 50; case "READY" -> 75; case "ISSUED", "SUPERSEDED" -> 100; default -> 0; };
        Phase reporting = new Phase("reporting", "Reporting", reportPct, reportPct / 25, 4, rs == null ? "no report yet" : "report stage: " + rs.toLowerCase().replace('_', ' '));

        List<Phase> phases = List.of(planning, fieldwork, testing, reporting);
        int overall = Math.round((planning.pct() + fieldwork.pct() + testing.pct() + reporting.pct()) / 4f);

        List<Milestone> ms = new ArrayList<>();
        boolean closed = "CLOSED".equals(in.status());
        boolean[] done = {
                true,
                planning.pct() == 100,
                fieldwork.pct() == 100,
                testing.total() > 0 && testing.pct() == 100,
                in.report() != null,
                in.report() != null && ("ISSUED".equals(rs) || "SUPERSEDED".equals(rs)),
        };
        String[][] defs = {
                {"created", "Engagement created"}, {"planning", "Planning and scope set"}, {"fieldwork", "Fieldwork complete"},
                {"testing", "Testing complete"}, {"draft", "Draft report started"}, {"issued", "Report issued"}};
        LocalDate[] dates = {
                in.createdOn(), null, in.startDate(), null,
                in.report() == null ? null : in.report().createdOn(), in.report() == null ? null : in.report().issuedOn()};
        boolean currentTaken = false;
        for (int i = 0; i < defs.length; i++) {
            String st;
            if (done[i] || closed) st = "DONE";
            else if (!currentTaken) { st = "CURRENT"; currentTaken = true; }
            else st = "PENDING";
            ms.add(new Milestone(defs[i][0], defs[i][1], dates[i], st));
        }
        if (in.endDate() != null)
            ms.add(new Milestone("end", "Planned end", in.endDate(), closed ? "DONE" : in.endDate().isBefore(today) ? "LATE" : "PENDING"));

        Counts c = new Counts(in.workpapers(), in.workpapersSignedOff(), in.findings(), in.openFindings(), in.findingsPastDue(),
                in.actions(), in.actionsValidated(), in.actionsOverdue(), in.team());
        return new Summary(overall, phases, ms, c, rs);
    }
}
