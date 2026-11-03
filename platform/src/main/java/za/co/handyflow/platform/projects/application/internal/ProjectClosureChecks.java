package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.dto.ClosureReadinessResponse;
import za.co.handyflow.platform.projects.dto.ClosureReadinessResponse.Check;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates whether a project can be formally closed.
 *
 * Uses COUNT queries through the EntityManager so no entities are hydrated
 * and no existing repository needed to change. The project itself must already
 * be tenant-verified by the caller — every query here is scoped by projectId.
 *
 * BLOCKING checks stop closure. Warnings are reported but never block.
 * The list is deliberately code-driven for now; later chunks (handover &
 * closeout engine) can make it configurable per project type.
 */
@Component
public class ProjectClosureChecks {

    @PersistenceContext
    private EntityManager em;

    public ClosureReadinessResponse evaluate(Project project) {
        UUID pid = project.getId();
        List<Check> checks = new ArrayList<>();

        checks.add(check("OPEN_TASKS", "Tasks that are not completed or cancelled", true,
                count("SELECT COUNT(t) FROM ProjectTask t WHERE t.projectId = :pid "
                        + "AND t.status NOT IN ('COMPLETED','CANCELLED')", pid)));

        // REJECTED = a resolution was rejected, so the defect is still unresolved.
        checks.add(check("UNRESOLVED_SNAGS", "Snag items that are open, in progress or rejected", true,
                count("SELECT COUNT(s) FROM SnagItem s WHERE s.projectId = :pid "
                        + "AND s.status IN ('OPEN','IN_PROGRESS','REJECTED')", pid)));

        checks.add(check("PENDING_CHANGE_ORDERS", "Change orders awaiting approval", true,
                count("SELECT COUNT(c) FROM ChangeOrder c WHERE c.projectId = :pid "
                        + "AND c.status = 'SUBMITTED'", pid)));

        checks.add(check("UNANSWERED_RFIS", "RFIs submitted but not yet answered", true,
                count("SELECT COUNT(r) FROM ProjectRfi r WHERE r.projectId = :pid "
                        + "AND r.status = 'SUBMITTED'", pid)));

        checks.add(check("UNAPPROVED_TIME", "Time entries awaiting approval", true,
                count("SELECT COUNT(t) FROM TimeEntry t WHERE t.projectId = :pid "
                        + "AND t.status = 'SUBMITTED'", pid)));

        // ── Warnings (never block) ────────────────────────────────────────────
        checks.add(check("DRAFT_CHANGE_ORDERS", "Change orders still in draft", false,
                count("SELECT COUNT(c) FROM ChangeOrder c WHERE c.projectId = :pid "
                        + "AND c.status = 'DRAFT'", pid)));

        checks.add(check("UNCLOSED_RFIS", "RFIs answered but not formally closed", false,
                count("SELECT COUNT(r) FROM ProjectRfi r WHERE r.projectId = :pid "
                        + "AND r.status = 'RESPONDED'", pid)));

        checks.add(check("OPEN_RED_RISKS", "Open RED risks", false,
                count("SELECT COUNT(r) FROM ProjectRisk r WHERE r.projectId = :pid "
                        + "AND r.rating = 'RED' AND r.status = 'OPEN'", pid)));

        // Document control: a closed project's register is frozen, so anything still open there is worth a last look.
        checks.add(check("DOCUMENTS_AWAITING_REVIEW", "Documents submitted for review but not yet approved or rejected", false,
                count("SELECT COUNT(d) FROM ProjectDocument d WHERE d.projectId = :pid "
                        + "AND d.status = 'FOR_REVIEW'", pid)));

        checks.add(check("UNACKNOWLEDGED_TRANSMITTALS", "Transmittals the recipient has not acknowledged", false,
                count("SELECT COUNT(t) FROM ProjectTransmittal t WHERE t.projectId = :pid "
                        + "AND t.acknowledgedAt IS NULL", pid)));

        boolean ready = checks.stream().noneMatch(c -> c.blocking() && !c.passed());
        return new ClosureReadinessResponse(ready, checks);
    }

    private long count(String jpql, UUID projectId) {
        return em.createQuery(jpql, Long.class)
                .setParameter("pid", projectId)
                .getSingleResult();
    }

    private static Check check(String code, String description, boolean blocking, long count) {
        return new Check(code, description, blocking, count, count == 0);
    }
}