package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseItemDetails;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CRUD for a client's SKU/item catalogue, scoped by clientId — see WhseItem's own Javadoc for why this is its own catalogue rather than a dependency on the shared `catalogue` module. */
@Service
@RequiredArgsConstructor
public class WhseItemService {

    private final WhseItemRepository repository;
    private final WhseClientService clientService;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<WhseItem> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        clientService.findActive(tenantId, clientId);
        return repository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    /** Unpaginated — used by the billing snapshot and order/shipment line pickers. */
    @Transactional(readOnly = true)
    public List<WhseItem> listAllActiveForClient(TenantId tenantId, UUID clientId) {
        return repository.findAllActiveForClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public WhseItem get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public WhseItem create(TenantId tenantId, UUID clientId, String sku, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth) {
        return create(tenantId, clientId, sku, description, uom, storageRatePerUnitPerMonth, WhseItemDetails.none(), null);
    }

    @Transactional
    public WhseItem create(TenantId tenantId, UUID clientId, String sku, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth, WhseItemDetails details, UUID actor) {
        clientService.findActive(tenantId, clientId);
        requireUniqueSku(tenantId, clientId, sku, null);
        requireUniqueBarcode(tenantId, clientId, details.barcode(), null);
        WhseItem item = WhseItem.create(tenantId.getValue(), clientId, sku, description, uom,
                storageRatePerUnitPerMonth);
        item.applyDetails(details);
        return repository.save(item);
    }

    @Transactional
    public WhseItem update(TenantId tenantId, UUID id, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth) {
        WhseItem item = findActive(tenantId, id);
        item.update(description, uom, storageRatePerUnitPerMonth);
        return repository.save(item);
    }

    /** Full replace of the editable fields. A change to cost or barcode is audited because both feed stock value and scanning. */
    @Transactional
    public WhseItem update(TenantId tenantId, UUID id, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth, WhseItemDetails details, UUID actor) {
        WhseItem item = findActive(tenantId, id);
        requireUniqueBarcode(tenantId, item.getClientId(), details.barcode(), id);
        WhseItemDetails before = item.details();
        item.update(description, uom, storageRatePerUnitPerMonth);
        item.applyDetails(details);
        WhseItem saved = repository.save(item);
        boolean costChanged = before.unitCost() == null ? details.unitCost() != null : details.unitCost() == null || before.unitCost().compareTo(details.unitCost()) != 0;
        boolean barcodeChanged = !java.util.Objects.equals(before.barcode(), details.barcode());
        if (costChanged || barcodeChanged) {
            audit(tenantId, actor, id, saved.getSku(), before, details);
        }
        return saved;
    }

    /** Live items matching a scanned barcode. More than one means several clients stock the same product. */
    @Transactional(readOnly = true)
    public List<WhseItem> findByBarcode(TenantId tenantId, String barcode, UUID clientId) {
        String code = za.co.handyflow.platform.warehousing.domain.model.Gtin.normalise(barcode);
        if (code == null) throw new IllegalArgumentException("Scan or type a barcode.");
        return repository.findByBarcode(tenantId.getValue(), code, clientId);
    }

    @Transactional
    public WhseItem deactivate(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.deactivate();
        return repository.save(item);
    }

    @Transactional
    public WhseItem reactivate(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.reactivate();
        return repository.save(item);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.softDelete();
        repository.save(item);
    }

    private void requireUniqueSku(TenantId tenantId, UUID clientId, String sku, UUID excludingId) {
        repository.findActiveByClientAndSku(tenantId.getValue(), clientId, sku).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new HandyFlowException("An item with SKU '" + sku + "' already exists for this client",
                        HttpStatus.CONFLICT, "DUPLICATE_SKU");
            }
        });
    }

    private void requireUniqueBarcode(TenantId tenantId, UUID clientId, String barcode, UUID excludingId) {
        if (barcode == null) return;
        repository.findActiveByClientAndBarcode(tenantId.getValue(), clientId, barcode).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new HandyFlowException("Barcode " + barcode + " is already used by item '" + existing.getSku() + "' for this client",
                        HttpStatus.CONFLICT, "DUPLICATE_BARCODE");
            }
        });
    }

    private void audit(TenantId tenantId, UUID actor, UUID itemId, String sku, WhseItemDetails before, WhseItemDetails after) {
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        String json = "{\"sku\":\"" + sku.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"unitCostBefore\":" + num(before.unitCost()) + ",\"unitCostAfter\":" + num(after.unitCost())
                + ",\"barcodeBefore\":" + str(before.barcode()) + ",\"barcodeAfter\":" + str(after.barcode()) + "}";
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                tenantId.getValue(), actor, name, "WHSE_ITEM_MASTER_CHANGED", "WHSE_ITEM", itemId, json);
    }

    private static String num(BigDecimal v) { return v == null ? "null" : v.toPlainString(); }
    private static String str(String v) { return v == null ? "null" : "\"" + v + "\""; }

    WhseItem findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseItem", id.toString()));
    }
}
