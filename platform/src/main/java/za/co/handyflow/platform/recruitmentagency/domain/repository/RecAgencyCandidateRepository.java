package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyCandidate;

import java.util.Optional;
import java.util.UUID;

public interface RecAgencyCandidateRepository extends JpaRepository<RecAgencyCandidate, UUID> {

    @Query("SELECT c FROM RecAgencyCandidate c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<RecAgencyCandidate> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
        SELECT c FROM RecAgencyCandidate c WHERE c.tenantId = :tenantId
        AND (:search IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(c.skills) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY c.updatedAt DESC
    """)
    Page<RecAgencyCandidate> search(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);
}