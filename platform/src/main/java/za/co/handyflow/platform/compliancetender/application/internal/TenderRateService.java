package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRate;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRateRepository;
import za.co.handyflow.platform.compliancetender.dto.ImportTenderRatesRequest;
import za.co.handyflow.platform.compliancetender.dto.SaveTenderRateRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderRateImportResult;
import za.co.handyflow.platform.compliancetender.dto.TenderRateResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The company's rates library and the import of a supplier's price list into it. A rate is identified by description + unit + supplier (ignoring case and extra spaces), so
 * importing the same list again updates costs in place instead of duplicating. A dry run reports what an import would do and changes nothing.
 */
@Service
@RequiredArgsConstructor
public class TenderRateService {

    static final int MAX_LISTED_CHANGES = 100;

    private final TenderRateRepository rates;

    @Transactional(readOnly = true)
    public List<TenderRateResponse> list(TenantId tenantId) {
        return rates.findAllForTenant(tenantId).stream().map(TenderRateService::toResponse).toList();
    }

    @Transactional
    public TenderRateResponse create(TenantId tenantId, SaveTenderRateRequest req, UUID by) {
        String category = req.category().trim().toUpperCase();
        String description = clean(req.description()), unit = clean(req.unit()), supplier = clean(req.supplier());
        if (rates.findByKey(tenantId, description, unit, supplier).isPresent()) throw new BusinessException(duplicateMessage(description, unit, supplier));
        try {
            return toResponse(rates.saveAndFlush(TenderRate.create(tenantId, category, req.itemRef(), description, unit, req.unitCost(), supplier, req.notes(), "MANUAL", by)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(duplicateMessage(description, unit, supplier));
        }
    }

    @Transactional
    public TenderRateResponse update(TenantId tenantId, UUID id, SaveTenderRateRequest req, UUID by) {
        TenderRate rate = find(tenantId, id);
        String category = req.category().trim().toUpperCase();
        String description = clean(req.description()), unit = clean(req.unit()), supplier = clean(req.supplier());
        rates.findByKey(tenantId, description, unit, supplier).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new BusinessException(duplicateMessage(description, unit, supplier)); });
        rate.update(category, req.itemRef(), description, unit, req.unitCost(), supplier, req.notes(), by);
        if (req.active() != null && req.active() != rate.isActive()) rate.setActive(req.active(), by);
        try {
            return toResponse(rates.saveAndFlush(rate));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(duplicateMessage(description, unit, supplier));
        }
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        rates.delete(find(tenantId, id));
    }

    /**
     * Reads the price list and creates, updates or leaves each rate. Rows that cannot be read are listed with their line and skipped; the rest still import. A re-imported rate is
     * set active again, since the supplier is listing it.
     */
    @Transactional
    public TenderRateImportResult importCsv(TenantId tenantId, ImportTenderRatesRequest req, UUID by) {
        RateCsvParser.Parsed parsed = RateCsvParser.parse(req.csv(), req.defaultCategory());
        List<TenderRateImportResult.RowProblem> problems = new ArrayList<>();
        if (parsed.fatal() != null) problems.add(new TenderRateImportResult.RowProblem(0, parsed.fatal()));
        parsed.problems().forEach(p -> problems.add(new TenderRateImportResult.RowProblem(p.line(), p.message())));
        String listSupplier = clean(req.supplier());
        String defaultCategory = RateCsvParser.defaultCategory(req.defaultCategory());

        int created = 0, updated = 0, unchanged = 0;
        List<TenderRateImportResult.PriceChange> changes = new ArrayList<>();
        for (RateCsvParser.Row row : parsed.rows()) {
            String supplier = row.supplier().isEmpty() ? listSupplier : row.supplier();
            TenderRate existing = rates.findByKey(tenantId, row.description(), row.unit(), supplier).orElse(null);
            if (existing == null) {
                created++;
                if (!req.dryRun()) rates.save(TenderRate.create(tenantId, row.category() != null ? row.category() : defaultCategory, row.itemRef(), row.description(), row.unit(), row.unitCost(), supplier, row.notes(), "IMPORT", by));
                continue;
            }
            boolean costMoved = existing.getUnitCost().compareTo(row.unitCost()) != 0;
            boolean otherChange = !existing.isActive() || (row.category() != null && !existing.getCategory().equals(row.category()))
                    || !same(existing.getItemRef(), row.itemRef()) || !same(existing.getNotes(), row.notes());
            if (!costMoved && !otherChange) { unchanged++; continue; }
            updated++;
            if (costMoved && changes.size() < MAX_LISTED_CHANGES) {
                changes.add(new TenderRateImportResult.PriceChange(existing.getDescription(), existing.getUnit(), existing.getSupplier(), existing.getUnitCost(), row.unitCost()));
            }
            if (!req.dryRun()) {
                existing.update(row.category() != null ? row.category() : existing.getCategory(), row.itemRef() != null ? row.itemRef() : existing.getItemRef(), existing.getDescription(), existing.getUnit(), row.unitCost(),
                        existing.getSupplier(), row.notes() != null ? row.notes() : existing.getNotes(), by);
                if (!existing.isActive()) existing.setActive(true, by);
            }
        }
        return new TenderRateImportResult(req.dryRun(), created, updated, unchanged, parsed.problems().size(), changes, problems);
    }

    private TenderRate find(TenantId tenantId, UUID id) {
        return rates.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("TenderRate", id.toString()));
    }

    private static boolean same(String a, String b) {
        // a blank cell in the file leaves what is stored as it is, so only a different non-blank value counts as a change
        return b == null || (a != null && a.equals(b));
    }

    static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String duplicateMessage(String description, String unit, String supplier) {
        return "A rate for \"" + description + "\"" + (unit.isEmpty() ? "" : " per " + unit) + (supplier.isEmpty() ? "" : " from " + supplier) + " is already in the library.";
    }

    private static TenderRateResponse toResponse(TenderRate r) {
        return new TenderRateResponse(r.getId(), r.getCategory(), r.getItemRef(), r.getDescription(), r.getUnit(), r.getUnitCost(), r.getSupplier(), r.getNotes(),
                r.isActive(), r.getPreviousUnitCost(), r.getPriceChangedAt(), r.getSource(), r.getUpdatedAt());
    }
}
