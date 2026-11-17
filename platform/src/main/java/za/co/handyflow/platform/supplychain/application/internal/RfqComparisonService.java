package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.RfqEvaluationRules.*;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RfqStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.RfqComparisonResponse;
import za.co.handyflow.platform.supplychain.dto.RfqResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierScorecardResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Side-by-side comparison of an RFQ's quotations using {@link RfqEvaluationRules}. The performance criterion
 * comes straight from the live supplier scorecards (chunk 5); B-BBEE from the supplier record.
 * app.scm.rfq.sealed-until-close=true hides quotations and the comparison until the RFQ is closed (sealed-bid practice).
 */
@Service
@RequiredArgsConstructor
public class RfqComparisonService {

    private final ScRfqRepository rfqRepo;
    private final ScRfqLineRepository lineRepo;
    private final ScQuotationRepository quoteRepo;
    private final ScQuotationLineRepository quoteLineRepo;
    private final ScSupplierRepository supplierRepo;
    private final SupplierScorecardService scorecards;

    @Value("${app.scm.rfq.sealed-until-close:false}")
    private boolean sealedUntilClose;

    public void requireUnsealed(ScRfq rfq) {
        if (sealedUntilClose && rfq.getStatus() == RfqStatus.OPEN)
            throw new HandyFlowException("Quotations are sealed until the RFQ closes", HttpStatus.CONFLICT, "RFQ_SEALED");
    }

    @Transactional(readOnly = true)
    public RfqComparisonResponse compare(TenantId tenantId, UUID rfqId, boolean enforceSeal) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.findByTenantAndId(tid, rfqId)
                .orElseThrow(() -> new HandyFlowException("RFQ not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (enforceSeal) requireUnsealed(rfq);

        List<ScRfqLine> rfqLines = lineRepo.findByRfq(tid, rfqId);
        List<ScQuotation> quotes = quoteRepo.findByRfq(tid, rfqId).stream()
                .filter(q -> q.getStatus() != QuotationStatus.WITHDRAWN).toList();

        Map<UUID, Integer> performance = new HashMap<>();
        if (rfq.getWeightPerformance() > 0 && !quotes.isEmpty()) {
            for (SupplierScorecardResponse s : scorecards.all(tenantId, null))
                if (s.score() != null) performance.put(s.supplierId(), s.score());
        }

        LocalDate today = LocalDate.now();
        List<QuoteIn> inputs = new ArrayList<>();
        Map<UUID, ScQuotation> byId = new HashMap<>();
        for (ScQuotation q : quotes) {
            byId.put(q.getId(), q);
            ScSupplier s = supplierRepo.findByTenantIdAndId(tid, q.getSupplierId()).orElse(null);
            boolean bbbeeExpired = s != null && s.getBbbeeExpiry() != null && s.getBbbeeExpiry().isBefore(today);
            List<QuoteLine> lines = quoteLineRepo.findByQuotation(tid, q.getId()).stream()
                    .map(l -> new QuoteLine(l.getRfqLineId(), l.getQtyOffered(), l.isNoBid())).toList();
            inputs.add(new QuoteIn(q.getId(), q.getSupplierId(), q.getSupplierName(), q.getLandedCost(), q.getLeadTimeDays(),
                    q.getTechnicalScore(), performance.get(q.getSupplierId()), s != null ? s.getBbbeeLevel() : null,
                    bbbeeExpired, q.getStatus() == QuotationStatus.DISQUALIFIED, q.getValidUntil(), lines));
        }

        Weights w = new Weights(rfq.getWeightPrice(), rfq.getWeightLeadTime(), rfq.getWeightTechnical(),
                rfq.getWeightPerformance(), rfq.getWeightBbbee());
        Result result = RfqEvaluationRules.evaluate(
                rfqLines.stream().map(l -> new LineReq(l.getId(), l.getQuantity())).toList(), inputs, w, today);

        BigDecimal estimated = BigDecimal.ZERO;
        boolean anyEstimate = false;
        for (ScRfqLine l : rfqLines) {
            if (l.getEstimatedUnitCost() != null) {
                estimated = estimated.add(l.getEstimatedUnitCost().multiply(l.getQuantity()));
                anyEstimate = true;
            }
        }
        estimated = estimated.setScale(2, RoundingMode.HALF_UP);

        List<RfqComparisonResponse.Row> rows = new ArrayList<>();
        for (Scored s : result.rows()) {
            ScQuotation q = byId.get(s.quotationId());
            BigDecimal saving = anyEstimate && s.complete() ? estimated.subtract(q.getLandedCost()) : null;
            rows.add(new RfqComparisonResponse.Row(q.getId(), q.getSupplierId(), q.getSupplierName(), q.getStatus().name(),
                    q.getLandedCost(), q.getTotalInclVat(), q.getLeadTimeDays(), q.getWarrantyMonths(), q.getPaymentTermsDays(),
                    q.getValidUntil(), s.complete(), s.expired(), s.ranked(), s.rank(), s.priceScore(), s.leadTimeScore(),
                    s.technicalScore(), s.performanceScore(), s.bbbeeScore(), s.totalScore(), saving, s.flags()));
        }
        return new RfqComparisonResponse(rfq.getId(), rfq.getRfqNumber(), rfq.getStatus().name(),
                new RfqResponse.Weights(w.price(), w.leadTime(), w.technical(), w.performance(), w.bbbee()),
                anyEstimate ? estimated : null, result.recommendedQuotationId(), result.lowestPriceQuotationId(), rows);
    }
}