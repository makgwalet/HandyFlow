package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.model.TimeEntry;

import java.util.List;
import java.util.UUID;

/**
 * Member-scoped aggregates for the dashboard and approval queue (restricted mode).
 * COUNT / SELECT through the EntityManager so no existing repository had to change.
 */
@Component
public class ProjectScopedQueries {

    @PersistenceContext
    private EntityManager em;

    private static final String MEMBER_PROJECTS =
            "(SELECT m.projectId FROM ProjectMember m WHERE m.tenantId = :tid AND m.userId = :uid)";

    public long countPendingTime(UUID tenantId, UUID userId) {
        return em.createQuery("SELECT COUNT(t) FROM TimeEntry t WHERE t.tenantId = :tid "
                        + "AND t.status = 'SUBMITTED' AND t.projectId IN " + MEMBER_PROJECTS, Long.class)
                .setParameter("tid", tenantId).setParameter("uid", userId)
                .getSingleResult();
    }

    public long countOpenRedRisks(UUID tenantId, UUID userId) {
        return em.createQuery("SELECT COUNT(r) FROM ProjectRisk r WHERE r.tenantId = :tid "
                        + "AND r.rating = 'RED' AND r.status = 'OPEN' AND r.projectId IN " + MEMBER_PROJECTS, Long.class)
                .setParameter("tid", tenantId).setParameter("uid", userId)
                .getSingleResult();
    }

    public List<TimeEntry> pendingTimeEntries(UUID tenantId, UUID userId) {
        return em.createQuery("SELECT t FROM TimeEntry t WHERE t.tenantId = :tid "
                        + "AND t.status = 'SUBMITTED' AND t.projectId IN " + MEMBER_PROJECTS
                        + " ORDER BY t.entryDate", TimeEntry.class)
                .setParameter("tid", tenantId).setParameter("uid", userId)
                .getResultList();
    }
}