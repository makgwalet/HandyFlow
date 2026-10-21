package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.model.ImportRules.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueImportJobRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Import and export of catalogue items as CSV.
 * <p>
 * The dry run ({@link #preview}) and the real import ({@link #run}) build the same plan from the same rules, so what the
 * dry run showed is what is applied. Each row is then saved through the ordinary item rules (duplicate checks, required
 * attributes, audit trail), one row at a time, so one bad row never undoes the others. The row that fails at that last
 * step is reported as failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueImportService {

    static final int LISTED_ROWS = 500, KEPT_PROBLEMS = 200;

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;
    private final CatalogueImportJobRepository jobRepository;
    private final CatalogueService catalogueService;
    private final CataloguePricingService pricing;

    /** The loaded state a plan is built against. */
    private record Context(List<Existing> existing, Map<String, UUID> categoryIds, List<String> categoryPaths, Map<UUID, CatalogueItem> itemsById) {}

    private Context context(TenantId tenantId) {
        Map<UUID, UUID> parents = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        categoryRepository.findAllActive(tenantId).forEach(c -> { parents.put(c.getId(), c.getParentId()); names.put(c.getId(), c.getName()); });
        Map<UUID, String> pathById = new HashMap<>();
        Map<String, UUID> idByPath = new LinkedHashMap<>();
        for (UUID id : names.keySet()) {
            String path = StructureRules.pathOf(id, parents::get, names::get).replace(" › ", " > ");
            pathById.put(id, path);
            idByPath.put(path.toLowerCase(Locale.ROOT).replaceAll("\\s*>\\s*", ">"), id);
        }
        Set<UUID> parentsWithVariants = new HashSet<>(itemRepository.parentIdsWithVariants(tenantId));
        List<Existing> existing = new ArrayList<>();
        Map<UUID, CatalogueItem> byId = new HashMap<>();
        for (CatalogueItem i : itemRepository.allLive(tenantId)) {
            byId.put(i.getId(), i);
            existing.add(new Existing(i.getId(), i.getSku(), i.getName(), i.getDescription(), i.getCategory() == null ? null : pathById.get(i.getCategory().getId()),
                    i.getUnit(), i.getBrand(), i.getBarcode(), i.getItemType(), i.getStatus(), i.getDefaultPrice(), i.getCostPrice(), i.getVatRate(),
                    i.isVatExempt(), i.getVersion() == null ? 0L : i.getVersion(), parentsWithVariants.contains(i.getId()), i.isVariant()));
        }
        return new Context(existing, idByPath, new ArrayList<>(pathById.values()), byId);
    }

    private Options options(TenantId tenantId, Caller caller) {
        return new Options(pricing.approvalRequired(tenantId), caller.canWritePrices(), false);
    }

    private Plan plan(TenantId tenantId, Table table, Mode mode, Context ctx, Caller caller) {
        return ImportRules.plan(table.rows().stream().map(ImportRules::parse).toList(), mode, ctx.existing(), ctx.categoryPaths(), options(tenantId, caller));
    }

    // ── Dry run ───────────────────────────────────────────────────────────────

    public ImportPreviewResponse preview(TenantId tenantId, ImportRequest r, Caller caller) {
        Table table = ImportRules.read(r.csv());
        if (!table.problems().isEmpty())
            return new ImportPreviewResponse(r.mode().name(), 0, 0, 0, 0, 0, table.problems(), table.unknownColumns(), List.of(), false);
        Plan plan = plan(tenantId, table, r.mode(), context(tenantId), caller);
        List<ImportPreviewResponse.RowResult> rows = new ArrayList<>();
        plan.rows().stream().filter(p -> p.action() == Action.ERROR).forEach(p -> rows.add(result(p)));
        plan.rows().stream().filter(p -> p.action() != Action.ERROR).forEach(p -> rows.add(result(p)));
        boolean truncated = rows.size() > LISTED_ROWS;
        return new ImportPreviewResponse(r.mode().name(), plan.rows().size(), plan.count(Action.CREATE), plan.count(Action.UPDATE), plan.count(Action.UNCHANGED),
                plan.count(Action.ERROR), List.of(), table.unknownColumns(), truncated ? rows.subList(0, LISTED_ROWS) : rows, truncated);
    }

    private static ImportPreviewResponse.RowResult result(Planned p) {
        return new ImportPreviewResponse.RowResult(p.line(), p.key(), p.action().name(), p.changes(), p.errors(), p.notes());
    }

    // ── Real import ───────────────────────────────────────────────────────────

    public ImportResultResponse run(TenantId tenantId, ImportRequest r, Caller caller) {
        Table table = ImportRules.read(r.csv());
        if (!table.problems().isEmpty()) throw new IllegalArgumentException(table.problems().get(0));
        Context ctx = context(tenantId);
        Plan plan = plan(tenantId, table, r.mode(), ctx, caller);
        boolean skipErrors = Boolean.TRUE.equals(r.skipErrors());
        if (plan.hasErrors() && !skipErrors)
            throw new IllegalArgumentException(plan.count(Action.ERROR) + " row(s) have problems. Fix them, or choose to leave those rows out.");

        int created = 0, updated = 0, unchanged = 0, skipped = 0, failed = 0;
        List<String> problems = new ArrayList<>();
        for (Planned p : plan.rows()) {
            switch (p.action()) {
                case ERROR -> { skipped++; problems.add("row " + p.line() + " (" + p.key() + "): " + String.join(" ", p.errors())); }
                case UNCHANGED -> unchanged++;
                case CREATE, UPDATE -> {
                    try {
                        if (p.action() == Action.CREATE) { create(tenantId, p.row(), ctx, caller); created++; }
                        else { update(tenantId, p, ctx, caller); updated++; }
                    } catch (RuntimeException e) {
                        failed++;
                        problems.add("row " + p.line() + " (" + p.key() + "): " + (e.getMessage() == null ? "could not be saved" : e.getMessage()));
                        log.warn("Catalogue import row {} failed: {}", p.line(), e.toString());
                    }
                }
            }
        }
        var job = jobRepository.save(CatalogueImportJob.of(tenantId, r.fileName(), r.mode(), plan.rows().size(), created, updated, unchanged, skipped, failed,
                String.join("\n", problems.size() > KEPT_PROBLEMS ? problems.subList(0, KEPT_PROBLEMS) : problems), caller.userId()));
        log.info("Catalogue import tenant={} rows={} created={} updated={} skipped={} failed={}", tenantId, plan.rows().size(), created, updated, skipped, failed);
        return new ImportResultResponse(job.getId(), plan.rows().size(), created, updated, unchanged, skipped, failed,
                problems.size() > KEPT_PROBLEMS ? problems.subList(0, KEPT_PROBLEMS) : problems);
    }

    private void create(TenantId tenantId, Parsed row, Context ctx, Caller caller) {
        CatalogueItemStatus status = row.status() != null ? row.status() : CatalogueItemStatus.ACTIVE;
        catalogueService.createItem(tenantId, new CreateItemRequest(row.name(), row.description(), categoryId(row.category(), ctx),
                row.unit() == null ? "each" : row.unit(), row.price(), row.vatRate(), row.sku(), row.barcode(), row.brand(), row.type(),
                row.cost(), row.vatExempt(), status, null), caller);
    }

    private void update(TenantId tenantId, Planned p, Context ctx, Caller caller) {
        Parsed row = p.row();
        CatalogueItem item = ctx.itemsById().get(p.itemId());
        boolean variant = item.isVariant();
        UUID category = variant ? categoryOf(item) : row.category() != null ? categoryId(row.category(), ctx) : categoryOf(item);
        catalogueService.updateItem(tenantId, item.getId(), new CreateItemRequest(
                variant || row.name() == null ? item.getName() : row.name(),
                row.description() != null ? row.description() : item.getDescription(),
                category,
                variant || row.unit() == null ? item.getUnit() : row.unit(),
                row.price() != null ? row.price() : item.getDefaultPrice(),
                variant || row.vatRate() == null ? item.getVatRate() : row.vatRate(),
                item.getSku(),
                row.barcode() != null ? row.barcode() : item.getBarcode(),
                variant || row.brand() == null ? item.getBrand() : row.brand(),
                variant || row.type() == null ? item.getItemType() : row.type(),
                row.cost() != null ? row.cost() : item.getCostPrice(),
                variant || row.vatExempt() == null ? item.isVatExempt() : row.vatExempt(),
                null, p.version(), null), caller);
    }

    private static UUID categoryOf(CatalogueItem i) { return i.getCategory() == null ? null : i.getCategory().getId(); }

    private static UUID categoryId(String cell, Context ctx) {
        if (cell == null) return null;
        String key = cell.toLowerCase(Locale.ROOT).replace('/', '>').replaceAll("\\s*>\\s*", ">").trim();
        UUID exact = ctx.categoryIds().get(key);
        if (exact != null) return exact;
        UUID found = null;
        for (var e : ctx.categoryIds().entrySet()) {
            String k = e.getKey();
            if (k.equals(key) || k.endsWith(">" + key)) { if (found != null) return null; found = e.getValue(); }
        }
        return found;
    }

    // ── Export, history ───────────────────────────────────────────────────────

    /** Every item that is not archived, in the template's columns. Cost goes out only to people allowed to see it. */
    public String export(TenantId tenantId, Caller caller) {
        Context ctx = context(tenantId);
        Map<UUID, String> pathById = new HashMap<>();
        ctx.existing().forEach(e -> pathById.put(e.id(), e.categoryPath()));
        List<String> header = new ArrayList<>(ImportRules.COLUMNS);
        StringBuilder out = new StringBuilder(ImportRules.csvLine(header, Set.of())).append("\r\n");
        for (Existing e : ctx.existing()) {
            if (e.status() == CatalogueItemStatus.ARCHIVED) continue;
            List<String> cells = List.of(nz(e.sku()), e.name(), nz(e.description()), nz(e.categoryPath()), nz(e.unit()), nz(e.brand()), nz(e.barcode()),
                    e.type().name().toLowerCase(Locale.ROOT), e.status().name().toLowerCase(Locale.ROOT), e.price().toPlainString(),
                    caller.canSeePrices() && e.cost() != null ? e.cost().toPlainString() : "", e.vatRate() == null ? "" : e.vatRate().stripTrailingZeros().toPlainString(), e.vatExempt() ? "yes" : "no");
            out.append(ImportRules.csvLine(cells, Set.of(9, 10, 11))).append("\r\n");
        }
        log.info("Catalogue export tenant={} user={} items={}", tenantId, caller.userId(), ctx.existing().size());
        return out.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public List<ImportJobResponse> jobs(TenantId tenantId) {
        return jobRepository.recent(tenantId, PageRequest.of(0, 25)).stream().map(j -> new ImportJobResponse(j.getId(), j.getFileName(), j.getMode().name(),
                j.getTotalRows(), j.getCreated(), j.getUpdated(), j.getUnchanged(), j.getSkipped(), j.getFailed(), j.getCreatedBy(), j.getCreatedAt())).toList();
    }
}
