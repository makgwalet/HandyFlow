package za.co.handyflow.platform.projects.domain.repository.projections;

import java.util.UUID;

/**
 * Interface projection returned by the batch stats native query on ProjectRepository.
 *
 * WHY AN INTERFACE PROJECTION?
 * ────────────────────────────
 * Spring Data JPA maps native query result columns to interface getter methods
 * by name (case-insensitive, underscore-stripped).  This is the lightest way to
 * receive a partial result set without building a full entity or a DTO class.
 * No constructor is needed; Spring generates a proxy at runtime.
 *
 * The alternative — Object[] — forces index-based access:
 *     ((Number) row[1]).longValue()   ← error-prone and unreadable
 *
 * With the projection:
 *     stats.getTaskCount()            ← self-documenting, refactor-safe
 */
public interface ProjectStatsSummary {

    /** The project UUID — returned as ::text from PostgreSQL, Spring converts to UUID. */
    UUID getProjectId();

    /** Total number of tasks (all statuses) belonging to this project. */
    long getTaskCount();

    /** Number of tasks whose status = 'COMPLETED'. */
    long getCompletedTaskCount();

    /** Number of risks whose status = 'OPEN'. */
    long getOpenRiskCount();
}