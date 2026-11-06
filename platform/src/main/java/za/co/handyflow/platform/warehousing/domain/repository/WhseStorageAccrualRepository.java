package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseStorageAccrual;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WhseStorageAccrualRepository extends JpaRepository<WhseStorageAccrual, UUID> {

    /** Positions of this tenant that already have an accrual for the day, so a re-run adds nothing. */
    @Query("SELECT a.inventoryId FROM WhseStorageAccrual a WHERE a.tenantId = :tenantId AND a.accrualDate = :date")
    List<UUID> findAccruedInventoryIds(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    /** Accruals not yet billed for dates after {@code after} up to and including {@code through}. */
    @Query("SELECT a FROM WhseStorageAccrual a WHERE a.tenantId = :tenantId AND a.clientId = :clientId "
            + "AND a.billingInvoiceId IS NULL AND a.accrualDate > :after AND a.accrualDate <= :through "
            + "ORDER BY a.accrualDate")
    List<WhseStorageAccrual> findUnbilled(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                          @Param("after") LocalDate after, @Param("through") LocalDate through);

    /** Gives the days back when an invoice is rejected (the database only allows this for a REJECTED invoice). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WhseStorageAccrual a SET a.billingInvoiceId = NULL WHERE a.billingInvoiceId = :invoiceId")
    int release(@Param("invoiceId") UUID invoiceId);

    @Query("SELECT i FROM WhseInventory i WHERE i.tenantId = :tenantId AND i.qtyOnHand > 0")
    List<za.co.handyflow.platform.warehousing.domain.model.WhseInventory> findStockedPositions(@Param("tenantId") UUID tenantId);
}
