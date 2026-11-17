package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.model.FuelPosTransaction;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportKeyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosTransactionRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentRequest;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Live pump transactions (sales, refunds, voids) from a sender, held with the shift they belong to.
 * <ul>
 *   <li>Same key, licence and Retail-division rules as the shift import. Idempotent per source + externalId: same content is a duplicate, different
 *       content is reported as a CONFLICT and not stored (send a correction under a new externalId).</li>
 *   <li>A transaction is tied to its shift by the shift's externalId, so it may arrive before or after the shift. Transactions for a shift that was
 *       already accepted or rejected are kept as NOT_APPLIED with a note: use the normal shift reversal or adjustment flow for those.</li>
 *   <li>Refunds and voids must point at a sale of the same shift. A void reverses the whole sale; refunds and voids together never exceed it.
 *       Problems are listed at receipt and checked again, as the authority, when the shift is accepted.</li>
 *   <li>Accepting the shift records each refund and void as a shift adjustment and approves it in the same step, so the supervisor who accepts is the
 *       approver and the expected takings drop by the same rule as a typed-in refund. The automatic accept never carries refunds or voids: it leaves
 *       that shift for a person.</li>
 *   <li>Sales are a record to compare with the meters and takings. They post no stock and no money.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPosTransactionService {

    private final FuelImportKeyRepository keys;
    private final FuelPosTransactionRepository txs;
    private final FuelPosImportRepository imports;
    private final FuelCashControlService cash;
    private final FuelDivisionGuard divisions;
    private final FeatureGuard featureGuard;
    private final ObjectMapper mapper;

    // ── ingest ───────────────────────────────────────────────────────────────

    @Transactional
    public TransactionIngestResult ingest(String rawKey, IncomingTransactions in) {
        FuelImportKey key = authenticate(rawKey);
        TenantId tenantId = key.getTenantId();
        if (!featureGuard.hasModuleFor(tenantId, "fuel")) throw new HandyFlowException("Fuel is not available for this company.", HttpStatus.FORBIDDEN, "FUEL_MODULE_UNAVAILABLE");
        divisions.require(tenantId, Set.of(FuelDivision.RETAIL_FORECOURT));
        checkShape(in);

        String source = in.source().trim(), shiftRef = in.shiftExternalId().trim();
        Optional<FuelPosImport> shift = imports.findByRef(tenantId, source, shiftRef);
        String closedNote = shift.filter(s -> !s.isPending()).map(s -> "The shift " + shiftRef + " was already " + s.getStatus().toLowerCase()
                + ", so this was kept as a record only. Use the shift reversal or adjustment flow in Fuel.").orElse(null);

        List<FuelPosTransaction> existingForShift = new ArrayList<>(txs.findByShiftRef(tenantId, source, shiftRef));
        List<TransactionOutcome> out = new ArrayList<>();
        List<FuelPosTransaction> fresh = new ArrayList<>();
        for (IncomingTransaction t : in.transactions()) {
            String ref = t.externalId().trim();
            String hash = hash(canonical(source, shiftRef, t));
            Optional<FuelPosTransaction> prior = txs.findByRef(tenantId, source, ref);
            if (prior.isPresent()) {
                FuelPosTransaction p = prior.get();
                boolean same = p.getPayloadHash().equals(hash);
                out.add(new TransactionOutcome(ref, same ? "DUPLICATE" : "CONFLICT",
                        same ? problemList(p.getProblems()) : List.of("Already received with different content. Send a correction under a new externalId.")));
                continue;
            }
            FuelPosTransaction row = FuelPosTransaction.receive(tenantId, key.getId(), source, shiftRef, ref, t.kind(),
                    blankToNull(t.originalExternalId()), blankToNull(t.pumpLabel()), t.litres(), t.amount(), blankToNull(t.tender()),
                    t.occurredAt(), blankToNull(t.reason()), hash);
            if (closedNote != null) row.notApplied(closedNote);
            fresh.add(row);
            existingForShift.add(row);
        }
        // Check the new rows against everything held for the shift, so a refund sent in the same batch as its sale is fine.
        Map<UUID, List<String>> problems = closedNote != null ? Map.of() : problemsByRow(existingForShift);
        for (FuelPosTransaction row : fresh) {
            if (closedNote == null) row.setProblems(String.join("\n", problems.getOrDefault(row.getId(), List.of())));
            txs.save(row);
            List<String> shown = closedNote != null ? List.of(closedNote) : problems.getOrDefault(row.getId(), List.of());
            out.add(new TransactionOutcome(row.getExternalId(), "STAGED", shown));
        }
        // Keep the answer in the order the sender used.
        Map<String, TransactionOutcome> byId = new HashMap<>();
        for (TransactionOutcome o : out) byId.putIfAbsent(o.externalId(), o);
        List<TransactionOutcome> ordered = new ArrayList<>();
        for (IncomingTransaction t : in.transactions()) ordered.add(byId.get(t.externalId().trim()));
        log.info("Fuel POS transactions received tenant={} source={} shift={} staged={} total={}", tenantId, source, shiftRef, fresh.size(), in.transactions().size());
        return new TransactionIngestResult(shiftRef, ordered);
    }

    /** Shape rules the annotations cannot express. Throws 400 and stages nothing. */
    static void checkShape(IncomingTransactions in) {
        Set<String> seen = new HashSet<>();
        for (IncomingTransaction t : in.transactions()) {
            String id = t.externalId().trim();
            if (!seen.add(id)) throw new IllegalArgumentException("Transaction " + id + " appears twice in this request.");
            if (FuelPosTransaction.SALE.equals(t.kind())) {
                if (blankToNull(t.pumpLabel()) == null) throw new IllegalArgumentException("Sale " + id + " needs a pumpLabel.");
                if (t.litres() == null) throw new IllegalArgumentException("Sale " + id + " needs litres.");
                if (blankToNull(t.originalExternalId()) != null) throw new IllegalArgumentException("Sale " + id + " cannot refer to another sale.");
            } else {
                if (blankToNull(t.originalExternalId()) == null) throw new IllegalArgumentException(t.kind() + " " + id + " needs originalExternalId (the sale it reverses).");
                if (t.originalExternalId().trim().equals(id)) throw new IllegalArgumentException(t.kind() + " " + id + " cannot reverse itself.");
                if (t.reason() == null || t.reason().trim().length() < 5) throw new IllegalArgumentException(t.kind() + " " + id + " needs a reason of at least 5 characters.");
            }
        }
    }

    /**
     * Problems per transaction, for the rows of one shift. Only refunds and voids can have problems: the sale they point at must be a sale of this shift,
     * a void must equal the sale, and the refunds and voids on one sale together must not exceed it.
     */
    static Map<UUID, List<String>> problemsByRow(List<FuelPosTransaction> rows) {
        Map<String, FuelPosTransaction> sales = new HashMap<>();
        for (FuelPosTransaction r : rows) if (r.isSale()) sales.put(r.getExternalId(), r);
        Map<String, BigDecimal> reversed = new HashMap<>();
        Map<UUID, List<String>> out = new HashMap<>();
        for (FuelPosTransaction r : rows) {
            if (!r.isReversal() || "NOT_APPLIED".equals(r.getStatus())) continue;
            List<String> list = out.computeIfAbsent(r.getId(), k -> new ArrayList<>());
            FuelPosTransaction sale = sales.get(r.getOriginalExternalId());
            if (sale == null) { list.add(r.getKind() + " " + r.getExternalId() + " refers to sale " + r.getOriginalExternalId() + ", which has not been received for this shift."); continue; }
            BigDecimal total = reversed.merge(sale.getExternalId(), r.getAmount(), BigDecimal::add);
            if (total.compareTo(sale.getAmount()) > 0)
                list.add(r.getKind() + " " + r.getExternalId() + " takes refunds and voids on sale " + sale.getExternalId() + " to R " + total.toPlainString()
                        + ", more than the sale of R " + sale.getAmount().toPlainString() + ".");
            if (FuelPosTransaction.VOID.equals(r.getKind()) && r.getAmount().compareTo(sale.getAmount()) != 0)
                list.add("VOID " + r.getExternalId() + " must equal the whole sale (R " + sale.getAmount().toPlainString() + "). Send a REFUND for a part.");
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    // ── review ───────────────────────────────────────────────────────────────

    /** The transactions held for a staged shift, with what they add up to beside the shift's meters. */
    @Transactional(readOnly = true)
    public ShiftTransactions forImport(TenantId tenantId, UUID importId) {
        FuelPosImport imp = imports.findByTenantAndId(tenantId, importId).orElseThrow(() -> new ResourceNotFoundException("FuelPosImport", importId.toString()));
        List<FuelPosTransaction> rows = txs.findByShiftRef(tenantId, imp.getSource(), imp.getExternalId());
        Map<UUID, List<String>> live = problemsByRow(rows);
        List<TransactionView> views = new ArrayList<>();
        for (FuelPosTransaction r : rows) {
            List<String> pr = r.isReceived() ? live.getOrDefault(r.getId(), List.of()) : problemList(r.getProblems());
            views.add(new TransactionView(r.getId(), r.getExternalId(), r.getKind(), r.getOriginalExternalId(), r.getPumpLabel(), r.getLitres(), r.getAmount(),
                    r.getTender(), r.getOccurredAt(), r.getReason(), r.getStatus(), pr, r.getAdjustmentId()));
        }
        return new ShiftTransactions(views, summarise(rows, meterLitres(imp)));
    }

    static TransactionSummary summarise(List<FuelPosTransaction> rows, BigDecimal meterLitres) {
        int sales = 0, notApplied = 0;
        BigDecimal litres = BigDecimal.ZERO, salesAmt = BigDecimal.ZERO, refunds = BigDecimal.ZERO, voids = BigDecimal.ZERO;
        Map<String, BigDecimal> net = new TreeMap<>();
        for (FuelPosTransaction r : rows) {
            if (FuelPosTransaction.NOT_APPLIED.equals(r.getStatus())) { notApplied++; continue; }
            String tender = r.getTender() == null ? "UNSPECIFIED" : r.getTender();
            if (r.isSale()) {
                sales++; litres = litres.add(r.getLitres() == null ? BigDecimal.ZERO : r.getLitres()); salesAmt = salesAmt.add(r.getAmount());
                net.merge(tender, r.getAmount(), BigDecimal::add);
            } else {
                if (FuelPosTransaction.VOID.equals(r.getKind())) voids = voids.add(r.getAmount()); else refunds = refunds.add(r.getAmount());
                net.merge(tender, r.getAmount().negate(), BigDecimal::add);
            }
        }
        BigDecimal diff = meterLitres == null ? null : litres.subtract(meterLitres);
        return new TransactionSummary(sales, litres, salesAmt, refunds, voids, salesAmt.subtract(refunds).subtract(voids), net, meterLitres, diff, notApplied);
    }

    private BigDecimal meterLitres(FuelPosImport imp) {
        try {
            IncomingShift s = mapper.readValue(imp.getPayload(), IncomingShift.class);
            BigDecimal total = BigDecimal.ZERO;
            for (IncomingReading r : s.readings()) {
                BigDecimal test = r.testLitres() == null ? BigDecimal.ZERO : r.testLitres();
                total = total.add(r.closingMeter().subtract(r.openingMeter()).subtract(test));
            }
            return total;
        } catch (JsonProcessingException e) { return null; }
    }

    // ── accept / reject of the shift ─────────────────────────────────────────

    /**
     * Called by the shift accept, after the shift is opened and before it is closed. Refunds and voids become approved adjustments (approved by
     * the supervisor accepting), which lowers the expected takings the close compares against. Anything that does not add up refuses the accept
     * and the whole accept rolls back. With no person (automatic accept) a shift that carries refunds or voids is refused so a person looks at it.
     *
     * @return how many refunds and voids were applied
     */
    @Transactional
    public int applyToShift(TenantId tenantId, String source, String shiftRef, UUID shiftId, UUID by) {
        List<FuelPosTransaction> rows = txs.findByShiftRef(tenantId, source, shiftRef).stream().filter(FuelPosTransaction::isReceived).toList();
        if (rows.isEmpty()) return 0;
        boolean anyReversal = rows.stream().anyMatch(FuelPosTransaction::isReversal);
        if (anyReversal && by == null) throw new IllegalStateException("This shift carries refunds or voids, so a supervisor must accept it.");
        Map<UUID, List<String>> problems = problemsByRow(rows);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Refunds and voids do not add up: " + String.join(" ", problems.values().stream().flatMap(List::stream).toList()));
        }
        int applied = 0;
        for (FuelPosTransaction r : rows) {
            if (r.isSale()) { r.applied(shiftId, null); txs.save(r); continue; }
            String reason = ("POS " + r.getKind().toLowerCase(Locale.ROOT) + " of sale " + r.getOriginalExternalId() + ": " + r.getReason());
            if (reason.length() > 300) reason = reason.substring(0, 300);
            var adj = cash.record(tenantId, shiftId, new AdjustmentRequest(r.getKind(), r.getAmount(), reason), null);
            cash.approve(tenantId, adj.id(), "Accepted with the imported shift " + shiftRef, by);
            r.applied(shiftId, adj.id());
            txs.save(r);
            applied++;
        }
        log.info("Fuel POS transactions applied shift={} source={} refundsAndVoids={} by={}", shiftId, source, applied, by);
        return applied;
    }

    /** When a staged shift is rejected its transactions are kept as a record and marked not applied. */
    @Transactional
    public void markNotApplied(TenantId tenantId, String source, String shiftRef) {
        for (FuelPosTransaction r : txs.findByShiftRef(tenantId, source, shiftRef)) {
            if (!r.isReceived()) continue;
            r.notApplied("The shift " + shiftRef + " was rejected, so this was not applied.");
            txs.save(r);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The same answer for a missing, unknown or revoked key. */
    private FuelImportKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required");
        FuelImportKey key = keys.findByKeyHash(FuelPosImportService.hashKey(rawKey.trim())).filter(FuelImportKey::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required"));
        key.touch(Instant.now());
        return key;
    }

    /** One fixed text for the content, so 10 and 10.00 are the same. */
    static String canonical(String source, String shiftRef, IncomingTransaction t) {
        return String.join("|", source, shiftRef, t.externalId().trim(), t.kind(), String.valueOf(t.occurredAt()),
                nz(t.pumpLabel()).toLowerCase(Locale.ROOT), n(t.litres()), n(t.amount()), nz(t.tender()), nz(t.originalExternalId()), nz(t.reason()));
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String n(BigDecimal v) { return v == null || v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString(); }
    private static String hash(String s) { return FuelPosImportService.hashKey(s); }
    private static List<String> problemList(String s) { return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n")); }
}
