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

    /**
     * What each stock position held on a calendar day, worked out from the movement ledger: the larger of the
     * balance it opened the day with and the highest balance it reached during the day. So stock received today
     * and stock shipped today are both billed for today. A position with no movements at all falls back to its
     * current balance (stock that pre-dates the ledger). Rows with nothing held are not returned.
     * Each row is {inventory id, quantity}.
     */
    @Query(value = """
            SELECT q.id, q.qty FROM (
              SELECT i.id,
                     CASE WHEN NOT EXISTS (SELECT 1 FROM whse_stock_movements m0
                                            WHERE m0.tenant_id = i.tenant_id AND m0.client_id = i.client_id AND m0.item_id = i.item_id
                                              AND m0.location_id = i.location_id AND m0.status = i.status AND m0.created_at < :dayEnd)
                          THEN (CASE WHEN i.created_at < :dayEnd THEN i.qty_on_hand ELSE 0 END)
                          ELSE GREATEST(
                                 COALESCE((SELECT m1.qty_after FROM whse_stock_movements m1
                                            WHERE m1.tenant_id = i.tenant_id AND m1.client_id = i.client_id AND m1.item_id = i.item_id
                                              AND m1.location_id = i.location_id AND m1.status = i.status AND m1.created_at < :dayStart
                                            ORDER BY m1.created_at DESC, m1.id DESC LIMIT 1), 0),
                                 COALESCE((SELECT max(m2.qty_after) FROM whse_stock_movements m2
                                            WHERE m2.tenant_id = i.tenant_id AND m2.client_id = i.client_id AND m2.item_id = i.item_id
                                              AND m2.location_id = i.location_id AND m2.status = i.status
                                              AND m2.created_at >= :dayStart AND m2.created_at < :dayEnd), 0))
                     END AS qty
                FROM whse_inventory i WHERE i.tenant_id = :tenantId) q
             WHERE q.qty > 0""", nativeQuery = true)
    List<Object[]> billableQuantities(@Param("tenantId") UUID tenantId, @Param("dayStart") java.time.Instant dayStart,
                                      @Param("dayEnd") java.time.Instant dayEnd);
}
