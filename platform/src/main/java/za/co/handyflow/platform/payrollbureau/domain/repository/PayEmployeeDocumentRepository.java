package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployeeDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayEmployeeDocumentRepository extends JpaRepository<PayEmployeeDocument, UUID> {

    @Query("SELECT d FROM PayEmployeeDocument d WHERE d.payEmployeeId = :employeeId ORDER BY d.uploadedAt DESC")
    List<PayEmployeeDocument> findByEmployee(@Param("employeeId") UUID employeeId);

    @Query("SELECT d FROM PayEmployeeDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<PayEmployeeDocument> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}