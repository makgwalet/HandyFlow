package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.Tolerances;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoiceLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType.*;

/**
 * Pure, stateless three-way matching: PO (what was authorised) vs accepted goods
 * receipts (what arrived) vs supplier invoice (what is charged). No repositories, no
 * Spring state — everything arrives in {@link Input}, so every rule is unit-testable
 * without a database (see ThreeWayMatchEngineTest).
 * <p>
 * Line-level mode (invoice has lines), per PO line:
 *   - quantity: invoiced qty must be <= accepted qty - qty already invoiced elsewhere
 *   - price:    invoice unit price must not exceed PO unit price beyond tolerance
 *     (undercharging is not blocked — it never costs the business money)
 *   - VAT:      line VAT rate must equal the PO line's; header VAT must equal sum of lines
 *   - totals:   header subtotal must equal sum of lines (within rounding)
 * Header-only mode (no lines): invoice total must fit within the value actually
 * received and not yet invoiced — NOT merely the PO total, so billing for undelivered
 * goods is caught.
 * <p>
 * Unresolved findings can never yield MATCHED; DISPUTE outranks PARTIAL_MATCH.
 */
@Component
public class ThreeWayMatchEngine {

    private static final Set<PoStatus> INVOICEABLE = EnumSet.of(
            PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED,
            PoStatus.PARTIALLY_RECEIVED, PoStatus.FULLY_RECEIVED, PoStatus.INVOICED);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Input(ScSupplierInvoice invoice,
                        List<ScSupplierInvoiceLine> invoiceLines,
                        ScPurchaseOrder po,
                        List<ScPoLine> poLines,
                        Map<UUID, BigDecimal> acceptedQtyByPoLine,
                        Map<UUID, BigDecimal> priorInvoicedQtyByPoLine,
                        BigDecimal priorInvoicedTotalForPo,
                        Boolean linkedGoodsReceiptPosted,   // null = no GR linked
                        long possibleDuplicates,
                        Tolerances tolerances) {}

    public record Finding(MatchExceptionType type, UUID poLineId, UUID invoiceLineId,
                          String message, BigDecimal expected, BigDecimal actual) {}

    public record Outcome(MatchStatus status, String summary, List<Finding> findings) {}

    public Outcome evaluate(Input in) {
        List<Finding> findings = new ArrayList<>();
        ScSupplierInvoice inv = in.invoice();
        ScPurchaseOrder po = in.po();

        if (!po.getSupplierId().equals(inv.getSupplierId())) {
            findings.add(new Finding(SUPPLIER_MISMATCH, null, null,
                    "Invoice supplier does not match the supplier on " + po.getOrderNumber(), null, null));
            return toOutcome(findings, MatchStatus.MATCHED, "");
        }
        if (!INVOICEABLE.contains(po.getStatus())) {
            findings.add(new Finding(PO_NOT_INVOICEABLE, null, null,
                    po.getOrderNumber() + " is " + po.getStatus() + " — only approved/sent/received orders can be invoiced",
                    null, null));
            return toOutcome(findings, MatchStatus.MATCHED, "");
        }
        if (Boolean.FALSE.equals(in.linkedGoodsReceiptPosted())) {
            findings.add(new Finding(MISSING_GRN, null, null,
                    "The linked goods receipt is not posted, so receipt cannot be confirmed", null, null));
        }
        if (in.possibleDuplicates() > 0) {
            findings.add(new Finding(POSSIBLE_DUPLICATE, null, null,
                    in.possibleDuplicates() + " other invoice(s) from this supplier have the same total and invoice date",
                    null, BigDecimal.valueOf(in.possibleDuplicates())));
        }

        if (in.invoiceLines().isEmpty()) matchHeaderOnly(in, findings);
        else matchLines(in, findings);

        String clean = in.invoiceLines().isEmpty()
                ? "PO + received value + invoice matched (header level — no invoice lines supplied)"
                : "PO + goods receipts + invoice matched line by line";
        return toOutcome(findings, MatchStatus.MATCHED, clean);
    }

    /** Public so the service can build an outcome for paths that never reach the PO checks. */
    public Outcome toOutcome(List<Finding> findings, MatchStatus statusWhenClean, String cleanSummary) {
        MatchStatus status = statusWhenClean;
        if (!findings.isEmpty()) {
            status = MatchStatus.PARTIAL_MATCH;
            for (Finding f : findings) {
                if (f.type().resultingStatus() == MatchStatus.DISPUTE) { status = MatchStatus.DISPUTE; break; }
            }
        }
        String summary = findings.isEmpty()
                ? cleanSummary
                : findings.size() + " matching exception(s): "
                  + String.join(", ", findings.stream().map(f -> f.type().name()).distinct().toList())
                  + " — see match exceptions";
        if (summary.length() > 250) summary = summary.substring(0, 250);
        return new Outcome(status, summary, findings);
    }

    // ── Line-level ────────────────────────────────────────────────────────────

    private void matchLines(Input in, List<Finding> findings) {
        Tolerances tol = in.tolerances();
        Map<UUID, ScPoLine> poLines = new HashMap<>();
        for (ScPoLine l : in.poLines()) poLines.put(l.getId(), l);

        Map<UUID, BigDecimal> invoicedQty = new LinkedHashMap<>();
        Map<UUID, UUID> firstInvoiceLine = new HashMap<>();
        BigDecimal linesSubtotal = BigDecimal.ZERO;
        BigDecimal linesVat = BigDecimal.ZERO;

        BigDecimal priceFactor = BigDecimal.ONE.add(tol.pricePct().divide(HUNDRED, 6, RoundingMode.HALF_UP));

        for (ScSupplierInvoiceLine line : in.invoiceLines()) {
            linesSubtotal = linesSubtotal.add(line.getLineTotal());
            linesVat = linesVat.add(line.getVatAmount());

            if (line.getPoLineId() == null) {
                findings.add(new Finding(UNMATCHED_LINE, null, line.getId(),
                        "Line " + line.getLineNumber() + " (" + line.getItemName() + ") is not linked to a PO line", null, null));
                continue;
            }
            ScPoLine poLine = poLines.get(line.getPoLineId());
            if (poLine == null) {
                findings.add(new Finding(LINE_NOT_ON_PO, line.getPoLineId(), line.getId(),
                        "Line " + line.getLineNumber() + " references a PO line that is not on " + in.po().getOrderNumber(), null, null));
                continue;
            }

            invoicedQty.merge(poLine.getId(), line.getQuantity(), BigDecimal::add);
            firstInvoiceLine.putIfAbsent(poLine.getId(), line.getId());

            BigDecimal ceiling = poLine.getUnitCost().multiply(priceFactor);
            if (line.getUnitPrice().compareTo(ceiling) > 0) {
                findings.add(new Finding(PRICE_VARIANCE, poLine.getId(), line.getId(),
                        "Line " + line.getLineNumber() + " (" + line.getItemName() + "): invoiced at R "
                                + money(line.getUnitPrice()) + " vs PO price R " + money(poLine.getUnitCost()),
                        poLine.getUnitCost(), line.getUnitPrice()));
            }
            if (line.getVatRate().compareTo(poLine.getVatRate()) != 0) {
                findings.add(new Finding(TAX_VARIANCE, poLine.getId(), line.getId(),
                        "Line " + line.getLineNumber() + ": VAT rate " + qty(line.getVatRate()) + "% vs PO "
                                + qty(poLine.getVatRate()) + "%",
                        poLine.getVatRate(), line.getVatRate()));
            }
        }

        BigDecimal qtyFactor = BigDecimal.ONE.add(tol.quantityPct().divide(HUNDRED, 6, RoundingMode.HALF_UP));
        for (Map.Entry<UUID, BigDecimal> e : invoicedQty.entrySet()) {
            UUID poLineId = e.getKey();
            ScPoLine poLine = poLines.get(poLineId);
            BigDecimal accepted = in.acceptedQtyByPoLine().getOrDefault(poLineId, BigDecimal.ZERO);
            BigDecimal prior = in.priorInvoicedQtyByPoLine().getOrDefault(poLineId, BigDecimal.ZERO);
            BigDecimal remaining = accepted.subtract(prior).max(BigDecimal.ZERO);

            if (accepted.compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(new Finding(MISSING_GRN, poLineId, firstInvoiceLine.get(poLineId),
                        poLine.getItemName() + ": invoiced " + qty(e.getValue()) + " but no accepted receipt is recorded",
                        BigDecimal.ZERO, e.getValue()));
            } else if (e.getValue().compareTo(remaining.multiply(qtyFactor)) > 0) {
                findings.add(new Finding(QUANTITY_VARIANCE, poLineId, firstInvoiceLine.get(poLineId),
                        poLine.getItemName() + ": invoiced " + qty(e.getValue()) + " but only " + qty(remaining)
                                + " accepted and not yet invoiced (accepted " + qty(accepted) + ", already invoiced " + qty(prior) + ")",
                        remaining, e.getValue()));
            }
        }

        BigDecimal r = tol.roundingAbs();
        if (linesSubtotal.subtract(in.invoice().getSubtotal()).abs().compareTo(r) > 0) {
            findings.add(new Finding(TOTAL_MISMATCH, null, null,
                    "Invoice lines add up to R " + money(linesSubtotal) + " but the invoice subtotal is R "
                            + money(in.invoice().getSubtotal()), linesSubtotal, in.invoice().getSubtotal()));
        }
        if (linesVat.subtract(in.invoice().getVatAmount()).abs().compareTo(r) > 0) {
            findings.add(new Finding(TAX_VARIANCE, null, null,
                    "VAT on lines is R " + money(linesVat) + " but the invoice VAT is R " + money(in.invoice().getVatAmount()),
                    linesVat, in.invoice().getVatAmount()));
        }
    }

    // ── Header-only ───────────────────────────────────────────────────────────

    private void matchHeaderOnly(Input in, List<Finding> findings) {
        Tolerances tol = in.tolerances();
        if (!tol.allowHeaderOnly()) {
            findings.add(new Finding(UNMATCHED_LINE, null, null,
                    "Invoice lines are required for matching under this tenant's policy", null, null));
            return;
        }

        BigDecimal acceptedValue = BigDecimal.ZERO;
        for (ScPoLine l : in.poLines()) {
            BigDecimal acc = in.acceptedQtyByPoLine().getOrDefault(l.getId(), BigDecimal.ZERO);
            BigDecimal net = acc.multiply(l.getUnitCost());
            BigDecimal gross = net.add(net.multiply(l.getVatRate()).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            acceptedValue = acceptedValue.add(gross);
        }
        acceptedValue = acceptedValue.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = in.invoice().getTotalAmount();
        BigDecimal factor = BigDecimal.ONE.add(tol.amountPct().divide(HUNDRED, 6, RoundingMode.HALF_UP));

        if (acceptedValue.compareTo(BigDecimal.ZERO) <= 0) {
            findings.add(new Finding(MISSING_GRN, null, null,
                    "No accepted goods receipt value is recorded against " + in.po().getOrderNumber(),
                    BigDecimal.ZERO, total));
        } else {
            BigDecimal prior = in.priorInvoicedTotalForPo() != null ? in.priorInvoicedTotalForPo() : BigDecimal.ZERO;
            BigDecimal headroom = acceptedValue.subtract(prior).max(BigDecimal.ZERO);
            if (total.compareTo(headroom.multiply(factor).add(tol.roundingAbs())) > 0) {
                findings.add(new Finding(AMOUNT_VARIANCE, null, null,
                        "Invoice total R " + money(total) + " exceeds the received-and-not-yet-invoiced value R "
                                + money(headroom) + " (received R " + money(acceptedValue) + ", already invoiced R " + money(prior) + ")",
                        headroom, total));
            }
        }

        BigDecimal poTotal = in.po().getTotalAmount();
        if (poTotal.compareTo(BigDecimal.ZERO) > 0
                && total.compareTo(poTotal.multiply(factor).add(tol.roundingAbs())) > 0
                && findings.stream().noneMatch(f -> f.type() == AMOUNT_VARIANCE)) {
            findings.add(new Finding(AMOUNT_VARIANCE, null, null,
                    "Invoice total R " + money(total) + " exceeds the PO total R " + money(poTotal), poTotal, total));
        }
    }

    private static String money(BigDecimal v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String qty(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
