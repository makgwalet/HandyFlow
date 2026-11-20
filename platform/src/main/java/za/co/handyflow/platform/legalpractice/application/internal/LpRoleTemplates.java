package za.co.handyflow.platform.legalpractice.application.internal;

import java.util.*;

import static za.co.handyflow.platform.legalpractice.application.internal.LpPermission.*;

/**
 * Starting points for a law firm's roles, as sets of {@link LpPermission}. A template is not a role: roles belong to a firm, so the firm creates a role and picks
 * a template as its starting set, then adjusts it. No template holds both sides of a maker-checker pair (see {@link LpAccessRules}); the partner template holds the
 * approving side only, so the person who approves is never the person who prepared.
 */
public final class LpRoleTemplates {

    private LpRoleTemplates() { }

    public record Template(String key, String name, String description, Set<LpPermission> permissions) { }

    private static Set<LpPermission> set(LpPermission... p) { return Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(p))); }

    private static final Map<String, Template> ALL = new LinkedHashMap<>();

    private static void add(String key, String name, String description, LpPermission... p) { ALL.put(key, new Template(key, name, description, set(p))); }

    static {
        add("partner", "Partner or director",
                "Runs the firm's matters and signs off the sensitive steps: approves trust payments, reconciliations and client due diligence, decides conflicts and concludes cases. Does not prepare what they approve.",
                READ, MANAGE, CONFLICT_RUN, CONFLICT_REVIEW, KYC_APPROVE, KYC_APPROVE_SENIOR, TRUST_PAYMENT_APPROVE, TRUST_RECONCILE_APPROVE, DEADLINE_COMPUTE, DOCUMENT_WRITE, DOCUMENT_HOLD,
                LITIGATION_MANAGE, LITIGATION_CONCLUDE, TASK_WRITE, WORKFLOW_MANAGE, REPORT_VIEW);
        add("attorney", "Attorney",
                "Works matters from opening to close: conflicts, due diligence preparation, deadlines, documents, court cases and tasks. Can request but not approve trust payments.",
                READ, MANAGE, CONFLICT_RUN, KYC_PREPARE, TRUST_PAYMENT_REQUEST, DEADLINE_COMPUTE, DOCUMENT_WRITE, LITIGATION_MANAGE, TASK_WRITE);
        add("candidate", "Candidate attorney or paralegal",
                "Prepares work for an attorney: due diligence, documents, deadlines, tasks and court dates. Cannot touch trust money or conclude a case.",
                READ, CONFLICT_RUN, KYC_PREPARE, DEADLINE_COMPUTE, DOCUMENT_WRITE, LITIGATION_MANAGE, TASK_WRITE);
        add("bookkeeper", "Trust bookkeeper",
                "Receives money into trust, requests payments out and prepares the monthly reconciliation. A partner approves.",
                READ, TRUST_RECEIVE, TRUST_PAYMENT_REQUEST, TRUST_RECONCILE, REPORT_VIEW);
        add("office", "Office administrator",
                "Runs the office: clients, matters, documents, tasks and workflows. No trust account and no financial reports.",
                READ, MANAGE, DOCUMENT_WRITE, TASK_WRITE, WORKFLOW_MANAGE);
        add("compliance", "Compliance officer",
                "Looks after the rules: client due diligence settings, deadline rules, ethical walls and who was turned away by them. Reads everything, changes no matter work.",
                READ, KYC_SETTINGS, DEADLINE_RULES, WALL_MANAGE, DOCUMENT_HOLD, REPORT_VIEW);
        add("auditor", "Read only",
                "Can look but not change anything. Includes the wall audit record and the reports.",
                READ, WALL_AUDIT, REPORT_VIEW);
    }

    public static Map<String, Template> all() { return Collections.unmodifiableMap(ALL); }
}
