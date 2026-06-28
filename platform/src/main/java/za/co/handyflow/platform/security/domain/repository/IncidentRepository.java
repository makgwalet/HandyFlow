// security/domain/repository/IncidentRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * IncidentRepository — intentionally thin.
 *
 * WHY so few methods?
 * The list query (getIncidents) requires dynamic WHERE clauses for status,
 * severity, and pagination with SQL-level sorting.  JPA @Query doesn't support
 * dynamic WHERE clauses cleanly (you'd need Spring Data JPA Specifications or
 * Querydsl, both of which add dependency overhead for one query).
 *
 * The fix from bug #2: IncidentService.getIncidents was loading ALL incidents
 * into memory then filtering in Java (full table scan + in-memory pagination +
 * N+1 for site/guard names).  The new IncidentService uses JdbcTemplate
 * with a dynamic SQL builder — the same pattern used in BookingsService.getBookings.
 * That keeps the JPA repository for single-entity operations (find, save) and
 * JDBC for the complex paginated list query.
 *
 * Junior dev note: This is a deliberate architectural choice, not laziness.
 * When a query is complex, dynamic, or performance-critical, JDBC gives you
 * full control of the SQL.  JPA is excellent for entity lifecycle management
 * (save, update, soft-delete).  Mixing them in the same service is perfectly
 * valid Spring practice — see BookingsService for the exact same pattern.
 */
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Query("""
        SELECT i FROM Incident i
        WHERE i.tenantId = :tenantId
        AND i.id         = :id
        """)
    Optional<Incident> findByIdAndTenantId(UUID id, TenantId tenantId);
}
