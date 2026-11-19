package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityEvent;

import java.util.List;
import java.util.UUID;

public interface ContractAuthorityEventRepository extends JpaRepository<ContractAuthorityEvent, UUID> {
    @Query("SELECT e FROM ContractAuthorityEvent e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<ContractAuthorityEvent> recent(@Param("tenantId") UUID tenantId, Pageable page);
}
