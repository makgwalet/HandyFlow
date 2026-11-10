package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocationAccrual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WhseLocationAccrualRepository extends JpaRepository<WhseLocationAccrual, UUID> {

    /** {client id, location id} pairs already accrued for the day, so a re-run adds nothing. */
    @Query("SELECT a.clientId, a.locationId FROM WhseLocationAccrual a WHERE a.tenantId = :tenantId AND a.accrualDate = :date")
    List<Object[]> findAccruedKeys(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("SELECT a FROM WhseLocationAccrual a WHERE a.tenantId = :tenantId AND a.clientId = :clientId "
            + "AND a.billingInvoiceId IS NULL AND a.accrualDate > :after AND a.accrualDate <= :through ORDER BY a.accrualDate")
    List<WhseLocationAccrual> findUnbilled(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                           @Param("after") LocalDate after, @Param("through") LocalDate through);

    /** Gives the days back when an invoice is rejected. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WhseLocationAccrual a SET a.billingInvoiceId = NULL WHERE a.billingInvoiceId = :invoiceId")
    int release(@Param("invoiceId") UUID invoiceId);
}
