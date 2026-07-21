package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccDocumentRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccDocumentRequestRepository extends JpaRepository<AccDocumentRequest, UUID> {

    @Query("""
        SELECT r FROM AccountantDocumentRequest r
        WHERE r.tenantId = :tenantId AND r.clientId = :clientId
        ORDER BY r.createdAt DESC
    """)
    List<AccDocumentRequest> findByTenantIdAndClientId(@Param("tenantId") UUID tenantId,
                                                       @Param("clientId") UUID clientId);

    @Query("SELECT r FROM AccountantDocumentRequest r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<AccDocumentRequest> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}