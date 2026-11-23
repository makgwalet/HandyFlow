package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgStockImportRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgStockImportRules.Result;
import za.co.handyflow.platform.agriculture.dto.StockImportRequest;
import za.co.handyflow.platform.agriculture.dto.StockImportResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creates stock items from CSV rows the screen has read. Every row is checked here ({@link AgStockImportRules}), an item name
 * already on the farm is skipped and never changed, and a dry run creates nothing. An opening quantity is posted as a RECEIPT so
 * the ledger explains the stock on hand. Everything that passes is created in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgStockImportService {

    private static final int LOOKUP_ROWS = 5000;
    private static final String OPENING_NOTE = "Opening stock (CSV import)";

    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final AgReferenceChecker references;

    @Transactional
    public StockImportResponse importItems(TenantId tenantId, UUID farmId, StockImportRequest req) {
        references.requireActiveFarm(tenantId, farmId);
        List<AgStockImportRules.Row> rows = req.rows() == null ? List.of() : req.rows().stream()
                .map(r -> new AgStockImportRules.Row(r.itemName(), r.category(), r.unit(), r.quantity(), r.unitCost(), r.reorderLevel(), r.supplier(), r.packSizeKg()))
                .toList();
        AgStockImportRules.requireRowCount(rows.size());

        Set<String> existing = new HashSet<>();
        for (AgInventoryItem i : inventoryItemRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, LOOKUP_ROWS)).getContent())
            existing.add(i.getItemName().trim().toLowerCase(Locale.ROOT));

        List<Result> results = AgStockImportRules.plan(rows, existing);

        if (!req.dryRun()) {
            List<AgInventoryItem> items = new ArrayList<>();
            List<AgStockMovement> openings = new ArrayList<>();
            for (Result r : results) {
                if (!AgStockImportRules.OK.equals(r.status())) continue;
                AgStockImportRules.Parsed p = r.parsed();
                AgInventoryItem item = AgInventoryItem.create(tenantId, farmId, p.itemName(), p.category(), p.unit(), p.reorderLevel(), p.unitCost(), p.supplier());
                item.setPackSize(p.packSizeKg());
                items.add(item);
                if (p.openingQuantity().signum() > 0) {
                    item.receive(p.openingQuantity(), null);
                    AgStockMovement opening = AgStockMovement.create(tenantId, item.getId(), "RECEIPT", AgDates.today(), p.openingQuantity(), p.unitCost(),
                            null, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), OPENING_NOTE);
                    opening.recordPackSize(item.getPackSizeKg());
                    openings.add(opening);
                }
            }
            inventoryItemRepository.saveAll(items);
            stockMovementRepository.saveAll(openings);
            log.info("Stock import farm={} created={} total={} tenant={}", farmId, items.size(), results.size(), tenantId.getValue());
        }

        return new StockImportResponse(req.dryRun(), results.size(),
                AgStockImportRules.count(results, AgStockImportRules.OK),
                AgStockImportRules.count(results, AgStockImportRules.DUPLICATE),
                AgStockImportRules.count(results, AgStockImportRules.ERROR),
                results.stream().map(r -> new StockImportResponse.RowResult(r.line(), r.itemName(), r.status(), r.message())).toList());
    }
}
