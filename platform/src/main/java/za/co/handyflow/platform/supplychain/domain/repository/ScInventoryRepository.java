package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScInventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScInventoryRepository extends JpaRepository<ScInventory, UUID> {

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId")
    List<ScInventory> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<ScInventory> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.locationId = :locationId")
    List<ScInventory> findByTenantIdAndLocation(@Param("tenantId") UUID tenantId,
                                                @Param("locationId") UUID locationId);

    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.locationId = :locationId AND i.catalogueItemId = :itemId")
    Optional<ScInventory> findByTenantIdAndLocationIdAndCatalogueItemId(
            @Param("tenantId")  UUID tenantId,
            @Param("locationId") UUID locationId,
            @Param("itemId")    UUID catalogueItemId);

    /**
     * Low-stock query for the dashboard list view.
     * Only returns items where a reorder point is set — items with reorderPoint = 0
     * have no threshold configured and should not appear as "low stock".
     */
    @Query("SELECT i FROM ScInventory i WHERE i.tenantId = :tenantId AND i.reorderPoint > 0 AND i.qtyOnHand <= i.reorderPoint")
    List<ScInventory> findLowStock(@Param("tenantId") UUID tenantId);

    /**
     * COUNT version of findLowStock — used by getSummary() so we don't load
     * full entity objects just to count them.
     *
     * WHY a separate COUNT query?
     * findLowStock().size() loads every column of every matching row across the
     * network, just to call .size() on the Java list. SELECT COUNT(*) is a single
     * integer returned by the DB — 10–100× faster.
     */
    @Query("SELECT COUNT(i) FROM ScInventory i WHERE i.tenantId = :tenantId AND i.reorderPoint > 0 AND i.qtyOnHand <= i.reorderPoint")
    long countLowStock(@Param("tenantId") UUID tenantId);

    /**
     * Upsert pattern for safe concurrent inventory creation.
     *
     * WHY native SQL instead of the JPA findByX + orElseGet pattern?
     * ──────────────────────────────────────────────────────────────
     * The findByX + orElseGet() approach has a TOCTOU (Time Of Check To Time Of
     * Use) race condition: two threads both find "no row", both try to INSERT,
     * the second gets a unique constraint violation and the GR post rolls back.
     *
     * INSERT ... ON CONFLICT DO NOTHING is atomic at the database level —
     * there is no gap between the check and the insert. If the row already exists,
     * PostgreSQL skips the insert silently. We then re-fetch with findBy... to
     * get the existing row regardless of which thread won the race.
     *
     * Note: This is a @Modifying query but it only INSERTs when the row doesn't
     * exist — effectively a no-op for existing rows. We always re-fetch afterward.
     */
    /**
     * Returns distinct tenant IDs that have at least one low-stock item.
     * Used by the weekly low-stock digest scheduler in ScmNotificationService
     * to know which tenant admins to email.
     *
     * WHY here and not on ScSupplierInvoiceRepository?
     * This query is about sc_inventory — it belongs in the inventory repository.
     * Putting inventory queries on the invoice repository creates confusing coupling
     * between unrelated domain concepts.
     */
    @Query(value = """
            SELECT DISTINCT inv.tenant_id
            FROM sc_inventory inv
            WHERE inv.reorder_point > 0
              AND inv.qty_on_hand <= inv.reorder_point
            """, nativeQuery = true)
    List<UUID> findTenantsWithLowStock();

    @Modifying
    @Query(value = """
            INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
                qty_on_hand, qty_reserved, qty_in_transit,
                reorder_point, reorder_qty, avg_cost, last_cost,
                expiry_tracking, lot_tracking, created_at, updated_at)
            VALUES (:id, :tenantId, :locationId, :catalogueItemId,
                0, 0, 0, 0, 0, 0, 0, false, false, NOW(), NOW())
            ON CONFLICT (tenant_id, location_id, catalogue_item_id) DO NOTHING
            """, nativeQuery = true)
    void upsertInventoryRow(@Param("id")              UUID id,
                            @Param("tenantId")         UUID tenantId,
                            @Param("locationId")       UUID locationId,
                            @Param("catalogueItemId")  UUID catalogueItemId);
}