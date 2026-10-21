package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.ReportDefResponse;
import za.co.handyflow.platform.catalogue.dto.ReportResponse;
import za.co.handyflow.platform.catalogue.dto.ReportResponse.Column;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The catalogue reports (see {@link ReportRules#ALL}). Each one reads the tenant's live items and works the answer out in
 * memory, up to {@link ReportRules#MAX_ROWS} rows, like the data-quality report. Reports that show cost or margin are
 * refused to people who may not see prices. Any report can be taken as CSV; text is guarded against spreadsheet formulas.
 */
@Service
@RequiredArgsConstructor
public class CatalogueReportService {

    /** Optional settings: a price list for PRICE_LIST, a margin limit for LOW_MARGIN, a number of days for PRICE_CHANGES and RECENT_CHANGES. */
    public record Params(UUID listId, Integer marginLimit, Integer days) {
        public static final Params NONE = new Params(null, null, null);
    }

    private final CatalogueItemRepository itemRepository;
    private final CatalogueItemMediaRepository mediaRepository;
    private final CatalogueItemChannelRepository publicationRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final CataloguePriceChangeRepository priceChangeRepository;
    private final CataloguePriceListRepository listRepository;
    private final CatalogueBundleComponentRepository bundleRepository;
    private final CataloguePricingService pricing;
    private Clock clock = Clock.system(CataloguePricingService.ZONE);

    void useClock(Clock clock) { this.clock = clock; }

    private static final class Table {
        final List<Column> columns = new ArrayList<>();
        final List<List<String>> rows = new ArrayList<>();
        long total;
        Table col(String key, String label) { columns.add(new Column(key, label, false)); return this; }
        Table num(String key, String label) { columns.add(new Column(key, label, true)); return this; }
        void row(String... cells) { total++; if (rows.size() < ReportRules.MAX_ROWS) rows.add(Arrays.asList(cells)); }
        boolean truncated() { return total > rows.size(); }
    }

    @Transactional(readOnly = true)
    public List<ReportDefResponse> reports(Caller caller) {
        return ReportRules.ALL.stream().map(d -> new ReportDefResponse(d.code(), d.group(), d.title(), d.description(), d.needsPrices(), !d.needsPrices() || caller.canSeePrices())).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse run(TenantId tenantId, String code, Params p, Caller caller) {
        var def = ReportRules.find(code).orElseThrow(() -> new ResourceNotFoundException("CatalogueReport", String.valueOf(code)));
        if (def.needsPrices() && !caller.canSeePrices()) throw new AccessDeniedException("This report shows costs and margins. You are not allowed to see them.");
        Params params = p == null ? Params.NONE : p;
        var ctx = new Ctx(tenantId);
        String[] note = {null};
        Table t = switch (def.code()) {
            case "PRICE_LIST" -> priceList(ctx, params, note);
            case "MARGIN" -> margin(ctx, null);
            case "LOW_MARGIN" -> { var limit = ReportRules.lowMarginLimit(params.marginLimit()); note[0] = "Items with a margin below " + limit.toPlainString() + "%."; yield margin(ctx, limit); }
            case "NO_COST" -> noCost(ctx);
            case "PRICE_CHANGES" -> priceChanges(ctx, ReportRules.days(params.days(), 90), note);
            case "PACKAGES" -> packages(ctx);
            case "BY_CATEGORY" -> byCategory(ctx);
            case "BY_STATUS" -> byStatus(ctx);
            case "RECENT_CHANGES" -> recent(ctx, ReportRules.days(params.days(), ReportRules.DEFAULT_DAYS), note);
            case "NO_PICTURE" -> noPicture(ctx);
            case "NO_BARCODE" -> noBarcode(ctx);
            default -> notPublished(ctx);
        };
        if (t.truncated()) note[0] = (note[0] == null ? "" : note[0] + " ") + "Showing the first " + ReportRules.MAX_ROWS + " of " + t.total + " rows.";
        return new ReportResponse(def.code(), def.title(), def.description(), t.columns, t.rows, t.total, t.truncated(), clock.instant(), note[0]);
    }

    @Transactional(readOnly = true)
    public String csv(TenantId tenantId, String code, Params p, Caller caller) {
        var r = run(tenantId, code, p, caller);
        Set<Integer> numeric = new HashSet<>();
        for (int i = 0; i < r.columns().size(); i++) if (r.columns().get(i).numeric()) numeric.add(i);
        StringBuilder out = new StringBuilder(ImportRules.csvLine(r.columns().stream().map(Column::label).toList(), Set.of())).append("\r\n");
        for (var row : r.rows()) out.append(ImportRules.csvLine(row, numeric)).append("\r\n");
        return out.toString();
    }

    // ── The tenant's items, loaded once per report ─────────────────────────────

    private final class Ctx {
        final TenantId tenant;
        final List<CatalogueItem> all;
        final Set<UUID> parents;
        final List<CatalogueItem> sellable;      // live items that are sold as they are (not parents of variants)
        final Map<UUID, CatalogueItem> byId;

        Ctx(TenantId tenant) {
            this.tenant = tenant;
            this.all = itemRepository.allLive(tenant);
            this.parents = new HashSet<>(itemRepository.parentIdsWithVariants(tenant));
            this.sellable = all.stream().filter(i -> !parents.contains(i.getId())).sorted(byName()).toList();
            this.byId = all.stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        }

        List<CatalogueItem> active() { return sellable.stream().filter(i -> i.getStatus() == CatalogueItemStatus.ACTIVE).toList(); }
    }

    private static Comparator<CatalogueItem> byName() { return Comparator.comparing(CatalogueItem::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(CatalogueItem::getId); }

    private static String cat(CatalogueItem i) { return i.getCategory() == null ? "" : i.getCategory().getName(); }

    private static String s(String v) { return v == null ? "" : v; }

    private static String label(CatalogueItem i) { return i.getName(); }

    // ── Reports ───────────────────────────────────────────────────────────────

    private Table priceList(Ctx c, Params p, String[] note) {
        var items = c.active();
        String listName = "Default list";
        UUID listId = p.listId();
        if (listId != null) {
            var l = listRepository.findLiveById(c.tenant, listId).orElseThrow(() -> new IllegalArgumentException("That price list does not exist."));
            listName = l.getName();
        }
        note[0] = "Price list: " + listName + ". Active items only; variants are listed instead of their main item.";
        Map<UUID, BigDecimal> prices = pricing.pricesOnList(c.tenant, listId, items);
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category").col("unit", "Unit").num("price", "Price").num("priceInclVat", "Price incl. VAT");
        for (var i : items) {
            BigDecimal price = prices.get(i.getId());
            t.row(label(i), s(i.getSku()), cat(i), s(i.getUnit()), ReportRules.money(price), ReportRules.money(PricingRules.withVat(price, i.getVatRate(), i.isVatExempt())));
        }
        return t;
    }

    private Table margin(Ctx c, BigDecimal below) {
        record Row(CatalogueItem item, BigDecimal margin) {}
        List<Row> rows = new ArrayList<>();
        for (var i : c.active()) {
            BigDecimal m = ReportRules.margin(i.getDefaultPrice(), i.getCostPrice());
            if (m != null && (below == null || m.compareTo(below) < 0)) rows.add(new Row(i, m));
        }
        rows.sort(Comparator.comparing(Row::margin).thenComparing(r -> r.item().getName(), String.CASE_INSENSITIVE_ORDER));
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category").num("price", "Price").num("cost", "Cost").num("margin", "Margin %");
        for (var r : rows) t.row(label(r.item()), s(r.item().getSku()), cat(r.item()), ReportRules.money(r.item().getDefaultPrice()), ReportRules.money(r.item().getCostPrice()), r.margin().toPlainString());
        return t;
    }

    private Table noCost(Ctx c) {
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category").num("price", "Price");
        for (var i : c.active()) {
            boolean priced = i.getDefaultPrice() != null && i.getDefaultPrice().signum() > 0;
            boolean noCost = i.getCostPrice() == null || i.getCostPrice().signum() <= 0;
            if (priced && noCost) t.row(label(i), s(i.getSku()), cat(i), ReportRules.money(i.getDefaultPrice()));
        }
        return t;
    }

    private Table priceChanges(Ctx c, int days, String[] note) {
        LocalDate today = LocalDate.now(clock);
        note[0] = "Changes that took effect from " + today.minusDays(days) + " to " + today + ".";
        Map<UUID, String> lists = new HashMap<>();
        listRepository.findAllLive(c.tenant).forEach(l -> lists.put(l.getId(), l.getName()));
        var t = new Table().col("date", "Effective").col("name", "Item").col("sku", "SKU").col("list", "Price list").num("old", "Old price").num("new", "New price").num("change", "Change %").col("reason", "Reason");
        for (var ch : priceChangeRepository.appliedSince(c.tenant, today.minusDays(days), PageRequest.of(0, ReportRules.MAX_ROWS + 1))) {
            var item = c.byId.get(ch.getItemId());
            String pct = ch.getOldPrice() != null && ch.getOldPrice().signum() > 0
                    ? ch.getNewPrice().subtract(ch.getOldPrice()).multiply(BigDecimal.valueOf(100)).divide(ch.getOldPrice(), 1, java.math.RoundingMode.HALF_UP).toPlainString() : "";
            t.row(ch.getEffectiveFrom().toString(), item == null ? "(deleted item)" : item.getName(), item == null ? "" : s(item.getSku()), s(lists.get(ch.getPriceListId())),
                    ReportRules.money(ch.getOldPrice()), ReportRules.money(ch.getNewPrice()), pct, s(ch.getReason()));
        }
        return t;
    }

    private Table packages(Ctx c) {
        Map<UUID, List<CatalogueBundleComponent>> parts = bundleRepository.allForTenant(c.tenant).stream().collect(Collectors.groupingBy(CatalogueBundleComponent::getBundleItemId));
        var t = new Table().col("name", "Package").col("sku", "SKU").num("parts", "Parts").num("partsTotal", "Parts together").num("price", "Package price").num("saving", "Saving").num("unpriced", "Parts without a price");
        for (var i : c.all.stream().filter(x -> x.getItemType() == CatalogueItemType.PACKAGE && x.getStatus() != CatalogueItemStatus.ARCHIVED).sorted(byName()).toList()) {
            List<BigDecimal> prices = new ArrayList<>(), qty = new ArrayList<>();
            for (var part : parts.getOrDefault(i.getId(), List.of())) {
                var item = c.byId.get(part.getComponentItemId());
                if (item == null) continue;
                prices.add(item.getDefaultPrice());
                qty.add(part.getQuantity());
            }
            BigDecimal total = BundleRules.total(prices, qty);
            int unpriced = BundleRules.unpriced(prices);
            BigDecimal saving = BundleRules.saving(i.getDefaultPrice(), total, unpriced);
            t.row(i.getName(), s(i.getSku()), String.valueOf(prices.size()), prices.isEmpty() ? "" : ReportRules.money(total), ReportRules.money(i.getDefaultPrice()), ReportRules.money(saving), String.valueOf(unpriced));
        }
        return t;
    }

    private Table byCategory(Ctx c) {
        Map<String, long[]> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, BigDecimal> sums = new HashMap<>();
        for (var i : c.sellable) {
            if (i.getStatus() == CatalogueItemStatus.ARCHIVED) continue;
            String k = i.getCategory() == null ? "No category" : i.getCategory().getName();
            long[] n = counts.computeIfAbsent(k, x -> new long[3]);
            n[0]++;
            if (i.getStatus() == CatalogueItemStatus.ACTIVE) {
                n[1]++;
                if (i.getDefaultPrice() != null && i.getDefaultPrice().signum() > 0) { n[2]++; sums.merge(k, i.getDefaultPrice(), BigDecimal::add); }
            }
        }
        var t = new Table().col("category", "Category").num("items", "Items").num("active", "Active").num("avg", "Average price");
        counts.forEach((k, n) -> t.row(k, String.valueOf(n[0]), String.valueOf(n[1]), n[2] == 0 ? "" : sums.get(k).divide(BigDecimal.valueOf(n[2]), 2, java.math.RoundingMode.HALF_UP).toPlainString()));
        return t;
    }

    private Table byStatus(Ctx c) {
        Map<CatalogueItemStatus, Long> n = c.all.stream().collect(Collectors.groupingBy(CatalogueItem::getStatus, Collectors.counting()));
        var t = new Table().col("status", "Status").num("items", "Items");
        for (var st : CatalogueItemStatus.values()) t.row(st.name().charAt(0) + st.name().substring(1).toLowerCase(Locale.ROOT), String.valueOf(n.getOrDefault(st, 0L)));
        return t;
    }

    private Table recent(Ctx c, int days, String[] note) {
        Instant since = clock.instant().minus(days, ChronoUnit.DAYS);
        note[0] = "Changes in the last " + days + " days. Each item appears once, with its latest change.";
        Map<UUID, CatalogueAuditEvent> latest = new LinkedHashMap<>();
        for (var e : auditRepository.itemLinesSince(c.tenant, since, PageRequest.of(0, ReportRules.MAX_ROWS * 4))) latest.putIfAbsent(e.getEntityId(), e);   // newest first
        var t = new Table().col("when", "When").col("name", "Item").col("sku", "SKU").col("what", "Latest change");
        ZoneId zone = CataloguePricingService.ZONE;
        for (var e : latest.values()) {
            var item = c.byId.get(e.getEntityId());
            t.row(e.getCreatedAt().atZone(zone).toLocalDateTime().withNano(0).toString().replace('T', ' '), item == null ? s(e.getEntityName()) + " (deleted)" : item.getName(),
                    item == null ? "" : s(item.getSku()), human(e.getAction()));
        }
        return t;
    }

    private Table noPicture(Ctx c) {
        Set<UUID> pictured = new HashSet<>(mediaRepository.itemIdsWithMedia(c.tenant, MediaRules.Kind.IMAGE));
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category").col("status", "Status");
        for (var i : c.all.stream().filter(x -> x.getParentId() == null && x.getStatus() != CatalogueItemStatus.ARCHIVED && !pictured.contains(x.getId())).sorted(byName()).toList())
            t.row(i.getName(), s(i.getSku()), cat(i), human(i.getStatus().name()));
        return t;
    }

    private Table noBarcode(Ctx c) {
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category").col("status", "Status");
        for (var i : c.sellable) {
            boolean goods = CompletenessRules.isGoods(i.getItemType());
            if (goods && i.getStatus() != CatalogueItemStatus.ARCHIVED && (i.getBarcode() == null || i.getBarcode().isBlank())) t.row(label(i), s(i.getSku()), cat(i), human(i.getStatus().name()));
        }
        return t;
    }

    private Table notPublished(Ctx c) {
        Set<UUID> published = new HashSet<>(publicationRepository.publishedItemIds(c.tenant));
        var t = new Table().col("name", "Item").col("sku", "SKU").col("category", "Category");
        for (var i : c.all.stream().filter(x -> x.getParentId() == null && x.getStatus() == CatalogueItemStatus.ACTIVE && !published.contains(x.getId())).sorted(byName()).toList())
            t.row(i.getName(), s(i.getSku()), cat(i));
        return t;
    }

    private static String human(Object name) {
        String n = String.valueOf(name).toLowerCase(Locale.ROOT).replace('_', ' ');
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
