package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderAcceptance;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderRevision;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.FuelOrderAcceptanceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelOrderRevisionRepository;
import za.co.handyflow.platform.fuel.dto.EnquiryDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** B06: the append-only trail of a quotation's versions, and the record of the customer's acceptance. Never edits or deletes. */
@Component
@RequiredArgsConstructor
public class FuelOrderHistory {

    private final FuelOrderRevisionRepository revisions;
    private final FuelOrderAcceptanceRepository acceptances;

    /** Records the order's current terms as the next revision. Call after the order has been created or revised. */
    @Transactional
    public void recordRevision(TenantId tenantId, FuelSalesOrder order, UUID by) {
        revisions.save(FuelOrderRevision.snapshot(order, revisions.latestNo(tenantId, order.getId()) + 1, by));
    }

    /** Records the acceptance against the latest revision. A second acceptance for the same order is refused, not overwritten. */
    @Transactional
    public void recordAcceptance(TenantId tenantId, FuelSalesOrder order, AcceptanceRequest req, LocalDate today, UUID by) {
        if (acceptances.findByOrder(tenantId, order.getId()).isPresent()) throw new ConflictException("An acceptance is already recorded for " + order.getOrderNumber() + ".");
        int latest = Math.max(1, revisions.latestNo(tenantId, order.getId()));
        acceptances.save(FuelOrderAcceptance.record(tenantId, order.getId(), latest, req.acceptedBy(), req.channel(), req.reference(), req.acceptedOn(), today, by));
    }

    @Transactional(readOnly = true)
    public OrderHistory history(TenantId tenantId, UUID orderId) {
        List<Revision> out = new ArrayList<>();
        BigDecimal previous = null;
        for (FuelOrderRevision r : revisions.findByOrder(tenantId, orderId)) {
            out.add(new Revision(r.getRevisionNo(), r.getSiteId(), r.getFuelType(), r.getLitres(), r.getPricePerLitre(), r.getRequestedDate(), r.getQuoteValidUntil(),
                    r.getCustomerReference(), r.getNotes(), r.getRecordedBy(), r.getRecordedAt(), previous == null ? null : r.getPricePerLitre().subtract(previous)));
            previous = r.getPricePerLitre();
        }
        Acceptance a = acceptances.findByOrder(tenantId, orderId).map(x -> new Acceptance(x.getRevisionNo(), x.getAcceptedBy(), x.getChannel(), x.getReference(),
                x.getAcceptedOn(), x.getRecordedBy(), x.getRecordedAt())).orElse(null);
        return new OrderHistory(out, a);
    }
}
