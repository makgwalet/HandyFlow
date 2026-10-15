package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.*;

/** Progress of an engagement's programme, overall and by domain. Pure. */
public final class ProgrammeSummary {
    private ProgrammeSummary() {}

    public record StepIn(String domain, String status, String conclusion, boolean needsFinding) {}
    public record Counts(int total, int completed, int notApplicable, int inProgress, int notStarted, int ineffective, int partial, int needingFinding, int donePct) {}
    public record Result(Counts overall, Map<String, Counts> byDomain) {}

    public static Counts count(Collection<StepIn> steps) {
        int total = steps.size(), done = 0, na = 0, ip = 0, ns = 0, bad = 0, part = 0, nf = 0;
        for (StepIn s : steps) {
            switch (s.status() == null ? "" : s.status()) {
                case "COMPLETED" -> done++;
                case "NOT_APPLICABLE" -> na++;
                case "IN_PROGRESS" -> ip++;
                default -> ns++;
            }
            if ("INEFFECTIVE".equals(s.conclusion())) bad++;
            if ("PARTIALLY_EFFECTIVE".equals(s.conclusion())) part++;
            if (s.needsFinding()) nf++;
        }
        int pct = total == 0 ? 0 : Math.round((done + na) * 100f / total);
        return new Counts(total, done, na, ip, ns, bad, part, nf, pct);
    }

    public static Result build(List<StepIn> steps) {
        Map<String, List<StepIn>> by = new LinkedHashMap<>();
        for (String d : ControlLibrary.DOMAINS) by.put(d, new ArrayList<>());
        for (StepIn s : steps) by.computeIfAbsent(s.domain(), k -> new ArrayList<>()).add(s);
        Map<String, Counts> out = new LinkedHashMap<>();
        by.forEach((d, l) -> { if (!l.isEmpty()) out.put(d, count(l)); });
        return new Result(count(steps), out);
    }
}
