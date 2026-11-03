package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps "an entity id in a URL" back to the project that owns it, so one interceptor
 * can enforce project access for routes like /tasks/{taskId}/status or /rfis/{rfiId}.
 *
 * Tenant-scoped: an id belonging to another tenant resolves to empty, exactly like
 * an id that doesn't exist. Entity names come from the fixed map below, never from
 * user input, so the dynamic JPQL is safe.
 *
 * KEEP IN SYNC with ProjectRoute.ENTITY_KEYWORDS — a keyword missing here simply
 * resolves to empty (request passes through to the controller); a keyword missing
 * THERE is denied as an unknown route.
 */
@Component
public class ProjectScopeResolver {

    private static final Map<String, String> ENTITY_BY_KEYWORD = Map.ofEntries(
            Map.entry("tasks",        "ProjectTask"),
            Map.entry("risks",        "ProjectRisk"),
            Map.entry("snags",        "SnagItem"),
            Map.entry("site-diaries", "SiteDiary"),
            Map.entry("documents",    "ProjectDocument"),
            Map.entry("budget",       "ProjectBudgetLine"),
            Map.entry("resources",    "ProjectResource"),
            Map.entry("change-orders","ChangeOrder"),
            Map.entry("rfis",         "ProjectRfi"),
            Map.entry("phases",       "ProjectPhase"),
            Map.entry("time-entries", "TimeEntry"),
            Map.entry("issues",       "ProjectIssue"),
            Map.entry("action-items", "ProjectActionItem"));

    @PersistenceContext
    private EntityManager em;

    public Optional<UUID> projectIdFor(String keyword, UUID entityId, UUID tenantId) {
        String entity = ENTITY_BY_KEYWORD.get(keyword);
        if (entity == null || entityId == null) return Optional.empty();
        List<UUID> rows = em.createQuery(
                        "SELECT e.projectId FROM " + entity + " e WHERE e.id = :id AND e.tenantId = :tid",
                        UUID.class)
                .setParameter("id", entityId)
                .setParameter("tid", tenantId)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }
}