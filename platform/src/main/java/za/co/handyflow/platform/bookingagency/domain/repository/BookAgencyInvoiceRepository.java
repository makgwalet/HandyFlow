package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyInvoice;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BookAgencyInvoiceRepository extends JpaRepository<BookAgencyInvoice, UUID> {

    @Query("SELECT i FROM BookAgencyInvoice i WHERE i.clientId = :clientId ORDER BY i.periodStart DESC")
    Page<BookAgencyInvoice> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM BookAgencyInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<BookAgencyInvoice> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    boolean existsByClientIdAndPeriodStart(UUID clientId, LocalDate periodStart);
}