package za.co.handyflow.platform.supplychain.application.internal;

import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure validation + totals for a proposed set of PO line changes — no repositories, so every
 * rule is unit-testable. Evaluated twice: when the amendment is raised, and again when it is
 * approved (receipts/invoices may have arrived in between).
 * <p>
 * Rules:
 *  - UPDATE: quantity must stay > 0 and >= what has already been received; price / VAT-rate
 *    changes are blocked on lines already billed by a non-cancelled supplier invoice.
 *  - REMOVE: blocked if anything has been received, receipted or invoiced against the line.
 *  - The PO must keep at least one line; a line can only be touched once per amendment.
 * Any line add / update / remove is a MATERIAL change (it changes what was approved).
 */
public final class PoAmendmentRules {

    private PoAmendmentRules() {}

    public static final String ADD = "ADD";
    public static final String UPDATE = "UPDATE";
    public static final String REMOVE = "REMOVE";

    public record Change(String action, UUID poLineId, UUID catalogueItemId, String itemName,
                         String supplierSku, BigDecimal qtyOrdered, BigDecimal unitCost, BigDecimal vatRate) {}

    public record ProposedLine(UUID poLineId, String itemName, BigDecimal qty, BigDecimal unitCost, BigDecimal vatRate) {}

    public record Result(List<ProposedLine> lines, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total,
                         List<String> errors, String summary, boolean linesChanged) {}

    public static Result evaluate(List<ScPoLine> current, List<Change> changes,
                                  Set<UUID> invoicedLineIds, Set<UUID> receiptedLineIds) {
        List<String> errors = new ArrayList<>();
        Map<UUID, ScPoLine> byId = new LinkedHashMap<>();
        current.forEach(l -> byId.put(l.getId(), l));

        Map<UUID, ProposedLine> replaced = new HashMap<>();
        Set<UUID> removed = new HashSet<>();
        List<ProposedLine> added = new ArrayList<>();
        Set<UUID> touched = new HashSet<>();
        int nAdd = 0, nUpd = 0, nRem = 0;

        for (Change c : changes) {
            String action = c.action() == null ? "" : c.action().trim().toUpperCase(Locale.ROOT);

            if (ADD.equals(action)) {
                if (c.itemName() == null || c.itemName().isBlank()) { errors.add("ADD: itemName is required"); continue; }
                if (c.qtyOrdered() == null || c.qtyOrdered().signum() <= 0) { errors.add("ADD " + c.itemName() + ": quantity must be greater than zero"); continue; }
                if (c.unitCost() == null || c.unitCost().signum() < 0) { errors.add("ADD " + c.itemName() + ": unit cost is required"); continue; }
                if (c.vatRate() == null) { errors.add("ADD " + c.itemName() + ": vat rate must be resolved before evaluation"); continue; }
                added.add(new ProposedLine(null, c.itemName(), c.qtyOrdered(), c.unitCost(), c.vatRate()));
                nAdd++;

            } else if (UPDATE.equals(action) || REMOVE.equals(action)) {
                ScPoLine line = c.poLineId() == null ? null : byId.get(c.poLineId());
                if (line == null) { errors.add(action + ": PO line " + c.poLineId() + " is not on this purchase order"); continue; }
                if (!touched.add(line.getId())) { errors.add(line.getItemName() + ": a line can only be changed once per amendment"); continue; }

                if (REMOVE.equals(action)) {
                    if (line.getQtyReceived().signum() > 0 || receiptedLineIds.contains(line.getId()))
                        errors.add(line.getItemName() + ": cannot be removed — goods have been received against it");
                    else if (invoicedLineIds.contains(line.getId()))
                        errors.add(line.getItemName() + ": cannot be removed — a supplier invoice is billed against it");
                    else { removed.add(line.getId()); nRem++; }
                } else {
                    BigDecimal qty = c.qtyOrdered() != null ? c.qtyOrdered() : line.getQtyOrdered();
                    BigDecimal cost = c.unitCost() != null ? c.unitCost() : line.getUnitCost();
                    BigDecimal vat = c.vatRate() != null ? c.vatRate() : line.getVatRate();
                    boolean priceOrVatChanged = cost.compareTo(line.getUnitCost()) != 0 || vat.compareTo(line.getVatRate()) != 0;
                    boolean anyChange = priceOrVatChanged || qty.compareTo(line.getQtyOrdered()) != 0;

                    if (!anyChange) errors.add(line.getItemName() + ": nothing to change");
                    else if (qty.signum() <= 0) errors.add(line.getItemName() + ": quantity must be greater than zero (use REMOVE to drop the line)");
                    else if (qty.compareTo(line.getQtyReceived()) < 0)
                        errors.add(line.getItemName() + ": quantity cannot go below the " + plain(line.getQtyReceived()) + " already received");
                    else if (cost.signum() < 0) errors.add(line.getItemName() + ": unit cost cannot be negative");
                    else if (priceOrVatChanged && invoicedLineIds.contains(line.getId()))
                        errors.add(line.getItemName() + ": price/VAT cannot change — a supplier invoice is already billed against it");
                    else { replaced.put(line.getId(), new ProposedLine(line.getId(), line.getItemName(), qty, cost, vat)); nUpd++; }
                }
            } else {
                errors.add("Unknown line action '" + c.action() + "' (use ADD, UPDATE or REMOVE)");
            }
        }

        List<ProposedLine> result = new ArrayList<>();
        for (ScPoLine l : current) {
            if (removed.contains(l.getId())) continue;
            ProposedLine r = replaced.get(l.getId());
            result.add(r != null ? r : new ProposedLine(l.getId(), l.getItemName(), l.getQtyOrdered(), l.getUnitCost(), l.getVatRate()));
        }
        result.addAll(added);
        if (result.isEmpty()) errors.add("A purchase order must keep at least one line");

        BigDecimal sub = BigDecimal.ZERO, vatSum = BigDecimal.ZERO;
        for (ProposedLine p : result) {
            BigDecimal net = p.qty().multiply(p.unitCost()).setScale(2, RoundingMode.HALF_UP);
            sub = sub.add(net);
            vatSum = vatSum.add(net.multiply(p.vatRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        boolean linesChanged = nAdd + nUpd + nRem > 0;
        String summary = linesChanged ? nAdd + " added, " + nUpd + " changed, " + nRem + " removed" : "";
        return new Result(result, sub, vatSum, sub.add(vatSum), errors, summary, linesChanged);
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
