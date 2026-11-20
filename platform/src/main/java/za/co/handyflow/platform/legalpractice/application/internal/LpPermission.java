package za.co.handyflow.platform.legalpractice.application.internal;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every Legal Practice authority, with where it belongs and how sensitive it is. The sensitivity and second-person flag mirror what the migrations put in the
 * {@code permissions} table; {@code LpPermissionCatalogueTest} checks this list against the authorities the controllers actually use, so a new one cannot be
 * forgotten here.
 */
public enum LpPermission {
    READ("Core", Kind.VIEW, Level.NORMAL, false, "View everything in Legal Practice that is not behind a finer permission"),
    MANAGE("Core", Kind.WORK, Level.NORMAL, false, "Day-to-day work: matters, clients, attorneys, time, disbursements, invoices, retainers, key dates"),
    ADMIN("Core", Kind.ADMIN, Level.SENSITIVE, false, "Everything, including every permission below. Give it to as few people as possible"),

    TRUST_RECEIVE("Trust account", Kind.WORK, Level.FINANCIAL, false, "Record money received into a client's trust account"),
    TRUST_PAYMENT_REQUEST("Trust account", Kind.REQUEST, Level.FINANCIAL, false, "Request a payment out of a client's trust account"),
    TRUST_PAYMENT_APPROVE("Trust account", Kind.APPROVE, Level.FINANCIAL, true, "Approve or reject a payment out of trust (a second person)"),
    TRUST_RECONCILE("Trust account", Kind.REQUEST, Level.FINANCIAL, false, "Prepare and submit a trust account reconciliation"),
    TRUST_RECONCILE_APPROVE("Trust account", Kind.APPROVE, Level.FINANCIAL, true, "Approve a reconciliation, which locks the period (a second person)"),

    CONFLICT_RUN("Conflicts", Kind.WORK, Level.NORMAL, false, "Run a conflict-of-interest check before opening a matter"),
    CONFLICT_REVIEW("Conflicts", Kind.APPROVE, Level.SENSITIVE, true, "Decide a check that found a possible conflict (a second person)"),

    KYC_PREPARE("Client due diligence", Kind.REQUEST, Level.NORMAL, false, "Prepare and submit a client's due diligence"),
    KYC_APPROVE("Client due diligence", Kind.APPROVE, Level.SENSITIVE, true, "Approve a client's due diligence (a second person)"),
    KYC_APPROVE_SENIOR("Client due diligence", Kind.APPROVE, Level.SENSITIVE, true, "Approve due diligence for a high-risk or politically exposed client"),
    KYC_SETTINGS("Client due diligence", Kind.ADMIN, Level.SENSITIVE, false, "Decide whether matters and trust receipts are blocked until a client is verified"),

    DEADLINE_COMPUTE("Deadlines", Kind.WORK, Level.NORMAL, false, "Work out a deadline from a rule and a trigger date, and correct it"),
    DEADLINE_RULES("Deadlines", Kind.ADMIN, Level.SENSITIVE, false, "Maintain the deadline rules and court calendar (a wrong rule gives wrong dates on every matter)"),

    DOCUMENT_WRITE("Documents", Kind.WORK, Level.NORMAL, false, "Upload matter documents and versions, check them out and in, archive them"),
    DOCUMENT_HOLD("Documents", Kind.ADMIN, Level.SENSITIVE, false, "Place a document under legal hold and release it"),
    DOCUMENT_OVERRIDE("Documents", Kind.ADMIN, Level.SENSITIVE, false, "Release a check-out held by someone else"),

    LITIGATION_MANAGE("Litigation", Kind.WORK, Level.NORMAL, false, "Open and maintain a court case: details, stage, hearings, court orders"),
    LITIGATION_CONCLUDE("Litigation", Kind.APPROVE, Level.SENSITIVE, false, "Conclude a court case with its outcome, and reopen one"),

    TASK_WRITE("Tasks and workflows", Kind.WORK, Level.NORMAL, false, "Create and work matter tasks: assign, start, complete, reopen, cancel"),
    WORKFLOW_MANAGE("Tasks and workflows", Kind.ADMIN, Level.NORMAL, false, "Create and change workflow templates, and apply them to matters"),

    WALL_MANAGE("Ethical walls", Kind.ADMIN, Level.SENSITIVE, false, "Put up and lift ethical walls that bar staff from a matter or client"),
    WALL_AUDIT("Ethical walls", Kind.VIEW, Level.SENSITIVE, false, "See the record of staff turned away by ethical walls"),

    REPORT_VIEW("Reports", Kind.VIEW, Level.FINANCIAL, false, "View and export firm reports: unbilled work, debtors, billing, trust balances");

    public enum Kind { VIEW, WORK, REQUEST, APPROVE, ADMIN }
    public enum Level { NORMAL, SENSITIVE, FINANCIAL }

    public static final String PREFIX = "LEGALPRACTICE_";

    private final String group;
    private final Kind kind;
    private final Level level;
    private final boolean secondPerson;
    private final String description;

    LpPermission(String group, Kind kind, Level level, boolean secondPerson, String description) {
        this.group = group; this.kind = kind; this.level = level; this.secondPerson = secondPerson; this.description = description;
    }

    public String code() { return PREFIX + name(); }
    public String group() { return group; }
    public Kind kind() { return kind; }
    public Level level() { return level; }
    /** True when the action needs a different person from whoever started it, and the system enforces that. */
    public boolean secondPerson() { return secondPerson; }
    public String description() { return description; }

    public static Optional<LpPermission> ofCode(String code) {
        return code == null ? Optional.empty() : Arrays.stream(values()).filter(p -> p.code().equals(code)).findFirst();
    }
}
