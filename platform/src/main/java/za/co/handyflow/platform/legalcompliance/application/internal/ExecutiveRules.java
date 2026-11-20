package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.AttentionItem;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Indicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure rules that turn a flat map of recorded counts ("findings.open" → 7) into indicators and attention items. No framework dependencies.
 *
 * Because everything is derived from the stored counts alone, a snapshot taken last March produces exactly the same indicators today as it did then,
 * even if the formulas here are later extended: older snapshots simply lack the newer keys, and an indicator with a missing input is omitted.
 * There is intentionally no single composite score: one number would hide which area is failing and invite false comfort.
 */
public final class ExecutiveRules {

    private ExecutiveRules() {}

    /** Whole-number percentage, or null when there is nothing to measure (denominator zero or negative). The numerator is clamped to 0..denominator. */
    public static Integer percent(long numerator, long denominator) {
        if (denominator <= 0) return null;
        long n = Math.max(0, Math.min(numerator, denominator));
        return (int) Math.round(100.0 * n / denominator);
    }

    public static List<Indicator> indicators(Map<String, Long> m) {
        List<Indicator> out = new ArrayList<>();
        add(out, m, "obligationsCompliant", "Obligations compliant", "compliant ÷ all obligations", "obligations", "obligations.compliant", "obligations.total");
        Long covered = sum(m, "coverage.coveredUntested", "coverage.coveredEffective");
        if (covered != null && m.containsKey("coverage.obligations")) {
            long d = m.get("coverage.obligations");
            out.add(new Indicator("controlCoverage", "Obligations covered by a control", "obligations with an active control ÷ all obligations", covered, d, percent(covered, d), "controls"));
        }
        add(out, m, "effectiveCoverage", "Obligations with an effective, tested control", "covered by a control that passed its latest test ÷ all obligations", "testing", "coverage.coveredEffective", "coverage.obligations");
        Long openFindings = m.get("findings.open"), overdueFindings = m.get("findings.overdue");
        if (openFindings != null && overdueFindings != null)
            out.add(new Indicator("findingsOnTime", "Open findings within their due date", "(open − overdue) ÷ open", openFindings - overdueFindings, openFindings, percent(openFindings - overdueFindings, openFindings), "findings"));
        Long openCapa = sum(m, "capa.proposed", "capa.approved", "capa.inProgress", "capa.awaitingVerification"), overdueCapa = m.get("capa.overdue");
        if (openCapa != null && overdueCapa != null)
            out.add(new Indicator("capaOnTime", "Open corrective actions within their due date", "(open − overdue) ÷ open", openCapa - overdueCapa, openCapa, percent(openCapa - overdueCapa, openCapa), "capa"));
        Long active = m.get("policies.active"), policyOverdue = m.get("policies.reviewOverdue");
        if (active != null && policyOverdue != null)
            out.add(new Indicator("policiesInDate", "Published policies within their review date", "(published − review overdue) ÷ published", active - policyOverdue, active, percent(active - policyOverdue, active), "policies"));
        add(out, m, "licencesValid", "Licences and permits valid", "valid (active, not expired) ÷ all live", "licences", "licences.valid", "licences.live");
        Long tpApproved = m.get("thirdParties.approved"), tpOverdue = m.get("thirdParties.reviewOverdue");
        if (tpApproved != null && tpOverdue != null)
            out.add(new Indicator("thirdPartiesCurrent", "Approved third parties reviewed on time", "(approved − review overdue) ÷ approved", tpApproved - tpOverdue, tpApproved, percent(tpApproved - tpOverdue, tpApproved), "thirdparties"));
        Long paiaOpen = m.get("privacy.paiaOpen"), paiaOverdue = m.get("privacy.paiaOverdue");
        if (paiaOpen != null && paiaOverdue != null)
            out.add(new Indicator("paiaOnTime", "Open PAIA requests within their due date", "(open − overdue) ÷ open", paiaOpen - paiaOverdue, paiaOpen, percent(paiaOpen - paiaOverdue, paiaOpen), "privacy"));
        Long actOpen = m.get("governance.actionsOpen"), actOverdue = m.get("governance.actionsOverdue");
        if (actOpen != null && actOverdue != null)
            out.add(new Indicator("committeeActionsOnTime", "Open committee actions within their due date", "(open − overdue) ÷ open", actOpen - actOverdue, actOpen, percent(actOpen - actOverdue, actOpen), "committees"));
        Long inForce = m.get("ethics.inForce"), reviewOverdue = m.get("ethics.reviewOverdue");
        if (inForce != null && reviewOverdue != null)
            out.add(new Indicator("declarationsInDate", "Cleared declarations within their review date", "(cleared or mitigated − review overdue) ÷ cleared or mitigated", inForce - reviewOverdue, inForce, percent(inForce - reviewOverdue, inForce), "ethics"));
        Long bcProcesses = m.get("continuity.processes"), bcNoPlan = m.get("continuity.withoutApprovedPlan");
        if (bcProcesses != null && bcNoPlan != null)
            out.add(new Indicator("continuityPlanned", "Business processes with an approved continuity plan", "(processes − without an approved plan) ÷ processes", bcProcesses - bcNoPlan, bcProcesses, percent(bcProcesses - bcNoPlan, bcProcesses), "continuity"));
        Long bcTestOverdue = m.get("continuity.testOverdue");
        if (bcProcesses != null && bcTestOverdue != null)
            out.add(new Indicator("continuityTested", "Business processes whose continuity test is in date", "(processes − test overdue) ÷ processes", bcProcesses - bcTestOverdue, bcProcesses, percent(bcProcesses - bcTestOverdue, bcProcesses), "continuity"));
        Long esgActive = m.get("esg.active"), esgOverdue = m.get("esg.readingsOverdue");
        if (esgActive != null && esgOverdue != null)
            out.add(new Indicator("esgReadingsOnTime", "ESG metrics with readings on time", "(active metrics − readings overdue) ÷ active metrics", esgActive - esgOverdue, esgActive, percent(esgActive - esgOverdue, esgActive), "esg"));
        return out;
    }

    private record Rule(String key, boolean critical, String text, String tab) {}

    private static final List<Rule> RULES = List.of(
            new Rule("obligations.nonCompliant", true, "obligation(s) marked non-compliant", "obligations"),
            new Rule("findings.critical", true, "critical finding(s) open", "findings"),
            new Rule("incidents.awaitingAssessment", true, "privacy incident(s) awaiting a notification assessment", "incidents"),
            new Rule("risks.outsideTolerance", true, "risk(s) outside tolerance", "risks"),
            new Rule("licences.expired", true, "licence(s) or permit(s) expired", "licences"),
            new Rule("licences.conditionsBreached", true, "licence condition(s) breached", "licences"),
            new Rule("privacy.paiaOverdue", true, "PAIA request(s) past their due date", "privacy"),
            new Rule("thirdParties.expired", true, "third-party document(s) expired", "thirdparties"),
            new Rule("changes.overdue", true, "regulatory change(s) overdue for assessment", "changes"),
            new Rule("continuity.criticalWithoutPlan", true, "critical process(es) with no approved continuity plan", "continuity"),
            new Rule("continuity.lastTestUnsuccessful", true, "continuity plan(s) whose latest test was unsuccessful", "continuity"),
            new Rule("governance.withoutQuorumCapacity", true, "committee(s) with too few members to ever reach a quorum", "committees"),
            new Rule("obligations.overdue", false, "obligation(s) overdue", "obligations"),
            new Rule("findings.overdue", false, "finding(s) overdue", "findings"),
            new Rule("capa.overdue", false, "corrective action(s) overdue", "capa"),
            new Rule("audits.overdue", false, "audit(s) overdue", "audits"),
            new Rule("testing.overdue", false, "control test(s) overdue", "testing"),
            new Rule("risks.overdueReview", false, "risk review(s) overdue", "risks"),
            new Rule("policies.reviewOverdue", false, "policy review(s) overdue", "policies"),
            new Rule("incidents.awaitingNotification", false, "incident(s) with notification duties still open", "incidents"),
            new Rule("privacy.filingsOverdue", false, "regulator filing(s) overdue", "privacy"),
            new Rule("privacy.piaReviewOverdue", false, "privacy impact assessment review(s) overdue", "privacy"),
            new Rule("thirdParties.reviewOverdue", false, "third-party review(s) overdue", "thirdparties"),
            new Rule("licences.conditionsOverdue", false, "licence condition(s) overdue", "licences"),
            new Rule("licences.expiring", false, "licence(s) or permit(s) due for renewal", "licences"),
            new Rule("governance.meetingsOverdue", false, "committee(s) overdue to meet", "committees"),
            new Rule("governance.charterReviewOverdue", false, "committee charter review(s) overdue", "committees"),
            new Rule("governance.actionsOverdue", false, "committee action(s) overdue", "committees"),
            new Rule("governance.minutesPending", false, "set(s) of minutes awaiting approval", "committees"),
            new Rule("ethics.awaitingReview", false, "conflict-of-interest declaration(s) awaiting review", "ethics"),
            new Rule("ethics.reviewOverdue", false, "declaration(s) past their review date", "ethics"),
            new Rule("ethics.giftsPending", false, "gift or hospitality entr(ies) awaiting approval", "ethics"),
            new Rule("continuity.testOverdue", false, "continuity test(s) overdue", "continuity"),
            new Rule("continuity.planReviewOverdue", false, "continuity plan review(s) overdue", "continuity"),
            new Rule("continuity.biaOverdue", false, "business impact analysis review(s) overdue", "continuity"),
            new Rule("esg.readingsOverdue", false, "ESG reading(s) overdue", "esg"),
            new Rule("esg.targetsMissed", false, "ESG target(s) missed", "esg"),
            new Rule("coverage.uncovered", false, "obligation(s) with no control", "controls"),
            new Rule("risks.unassessed", false, "risk(s) not yet assessed", "risks"));

    /** Critical items first, then warnings, each group in the order above. Only counts above zero appear. */
    public static List<AttentionItem> attention(Map<String, Long> m) {
        List<AttentionItem> out = new ArrayList<>();
        for (boolean critical : new boolean[]{true, false})
            for (Rule r : RULES) {
                Long c = m.get(r.key());
                if (r.critical() == critical && c != null && c > 0) out.add(new AttentionItem(r.key(), critical ? "CRITICAL" : "WARNING", c + " " + r.text(), c, r.tab()));
            }
        return out;
    }

    private static void add(List<Indicator> out, Map<String, Long> m, String key, String label, String formula, String tab, String numKey, String denKey) {
        Long n = m.get(numKey), d = m.get(denKey);
        if (n != null && d != null) out.add(new Indicator(key, label, formula, n, d, percent(n, d), tab));
    }

    private static Long sum(Map<String, Long> m, String... keys) {
        long total = 0;
        for (String k : keys) { Long v = m.get(k); if (v == null) return null; total += v; }
        return total;
    }
}
