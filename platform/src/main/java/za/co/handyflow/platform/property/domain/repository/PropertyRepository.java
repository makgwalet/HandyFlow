// property/domain/repository/PropertyRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Property;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    @Query("SELECT p FROM Property p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.name")
    Page<Property> findAllActive(TenantId tenantId, Pageable pageable);

    // NEW: backs the fix to PropertyService.getProperties(), which was
    // previously passing List.of() (a hardcoded empty list) for every
    // property's units — every property in the list view showed "0 units,
    // 0% occupied" regardless of how many units actually existed.
    //
    // Deliberately NOT `LEFT JOIN FETCH p.units` on findAllActive above —
    // combining a collection fetch with Pageable is a known Hibernate
    // anti-pattern: Hibernate can't apply LIMIT/OFFSET at the SQL level
    // when a collection join could multiply row counts, so it silently
    // loads every matching row into memory and paginates there instead,
    // defeating the entire point of a paginated query. A separate,
    // lightweight GROUP BY count avoids that — one query, one row per
    // property, no entity hydration at all.
    @Query("""
            SELECT u.property.id, COUNT(u),
                   SUM(CASE WHEN u.status = 'VACANT' THEN 1L ELSE 0L END),
                   SUM(CASE WHEN u.status = 'OCCUPIED' THEN 1L ELSE 0L END)
            FROM Unit u
            WHERE u.tenantId = :tenantId AND u.deletedAt IS NULL
            GROUP BY u.property.id
            """)
    java.util.List<Object[]> countUnitsByProperty(TenantId tenantId);

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.units WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Property> findActiveByIdWithUnits(TenantId tenantId, UUID id);

    @Query("SELECT p FROM Property p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Property> findActiveById(TenantId tenantId, UUID id);
}