package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayFeeNote;

import java.util.Optional;
import java.util.UUID;

public interface PayFeeNoteRepository extends JpaRepository<PayFeeNote, UUID> {

    @Query("SELECT f FROM PayFeeNote f WHERE f.payClientId = :clientId ORDER BY f.invoiceDate DESC")
    Page<PayFeeNote> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT f FROM PayFeeNote f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<PayFeeNote> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}