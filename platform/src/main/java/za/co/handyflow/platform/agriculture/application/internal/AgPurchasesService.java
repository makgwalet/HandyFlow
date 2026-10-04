package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.application.internal.AgPurchasesAggregator.Row;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierOption;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.SupplierFacade;
import za.co.handyflow.platform.supplychain.application.SupplierFacade.SupplierSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Suppliers and spend by supplier (ADR-001, W7). Supply Chain owns suppliers (read through {@link SupplierFacade}, identity only); Agriculture records which supplier
 * a stock receipt came from and reports what those receipts cost. It never creates or edits a supplier.
 */
@Service
@RequiredArgsConstructor
public class AgPurchasesService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int DEFAULT_RANGE_DAYS = 365;
    private static final int MAX_RANGE_DAYS = 1095;
    private static final int MAX_SUPPLIERS = 500;

    private final SupplierFacade supplierFacade;
    private final AgFarmRepository farmRepository;
    private final AgStockMovementRepository stockMovementRepository;

    /** The suppliers a purchase can be linked to: ACTIVE ones, by name. */
    @Transactional(readOnly = true)
    public List<SupplierOption> suppliers(TenantId tenantId) {
        return supplierFacade.listActive(tenantId, MAX_SUPPLIERS).stream().map((SupplierSummary s) -> new SupplierOption(s.id(), s.name())).toList();
    }

    /** What the farm's stock receipts cost, by supplier, in the range (default the last 365 days). */
    @Transactional(readOnly = true)
    public SupplierSpendResponse bySupplier(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        if (start.isAfter(end)) throw new IllegalArgumentException("from cannot be after to");
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) throw new IllegalArgumentException("choose a range of at most " + MAX_RANGE_DAYS + " days");

        List<Row> rows = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (Object[] r : stockMovementRepository.receiptSpendBySupplier(tenantId, farmId, start, end)) {
            UUID supplierId = (UUID) r[0];
            if (supplierId != null) ids.add(supplierId);
            rows.add(new Row(supplierId, ((Number) r[1]).longValue(), (BigDecimal) r[2], r[3] == null ? 0 : ((Number) r[3]).longValue()));
        }
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) supplierFacade.findAll(tenantId, ids).forEach((k, v) -> names.put(k, v.name()));
        return AgPurchasesAggregator.build(start, end, rows, names);
    }
}
