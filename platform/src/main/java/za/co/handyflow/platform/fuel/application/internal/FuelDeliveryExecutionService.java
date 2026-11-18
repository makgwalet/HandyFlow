package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.DeliveryExecutionDtos.Event;
import za.co.handyflow.platform.fuel.dto.DeliveryExecutionDtos.Execution;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Puts what happened to one load in time order, from the records that already exist. Read-only; changes nothing. */
@Service
@RequiredArgsConstructor
public class FuelDeliveryExecutionService {

    private final FuelDeliveryRepository deliveries;
    private final FuelArrivalCheckService arrivalChecks;
    private final FuelEvidenceService evidence;
    private final FuelDeliveryExceptionService exceptions;

    @Transactional(readOnly = true)
    public Execution execution(TenantId tenantId, UUID deliveryId) {
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        List<Event> ev = new ArrayList<>();
        if (d.getCreatedAt() != null) ev.add(new Event(d.getCreatedAt(), "SCHEDULED", "Load created" + (d.getScheduledAt() != null ? ", scheduled for " + d.getScheduledAt() : ""), "OK"));
        if (d.getLoadedAt() != null) ev.add(new Event(d.getLoadedAt(), "LOADED", "Loaded " + litres(d.getLoadedLitres()) + (blank(d.getSealNumbers()) ? "" : ", seals " + d.getSealNumbers()), "OK"));

        for (var c : arrivalChecks.history(tenantId, deliveryId)) {
            boolean review = c.findings().stream().anyMatch(f -> f.severity() == za.co.handyflow.platform.fuel.dto.EligibilityDtos.Severity.REVIEW);
            String text = (c.allowed() ? "Arrival check passed" : "Arrival check blocked") + (c.findings().isEmpty() ? "" : ": " + String.join("; ", c.findings().stream().map(Finding::message).toList()));
            ev.add(new Event(c.checkedAt(), "ARRIVAL_CHECK", text, !c.allowed() ? "PROBLEM" : review ? "REVIEW" : "OK"));
        }
        for (var e : evidence.list(tenantId, deliveryId)) {
            boolean replaced = e.supersededReason() != null || "SUPERSEDED".equals(e.status());
            String where = e.latitude() != null && e.longitude() != null ? " at " + e.latitude().setScale(5, java.math.RoundingMode.HALF_UP) + ", " + e.longitude().setScale(5, java.math.RoundingMode.HALF_UP) : "";
            ev.add(new Event(e.capturedAt(), "EVIDENCE", label(e.kind()) + " captured" + where + (blank(e.note()) ? "" : " (" + e.note() + ")")
                    + (replaced ? ", replaced" + (blank(e.supersededReason()) ? "" : ": " + e.supersededReason()) : e.verifiedAt() != null ? ", verified" : ""), replaced ? "REVIEW" : "OK"));
        }
        for (var x : exceptions.list(tenantId, deliveryId)) {
            ev.add(new Event(x.recordedAt(), "FAILED_ATTEMPT", "Attempt " + x.attemptNumber() + " failed: " + label(x.reasonCode()) + (blank(x.note()) ? "" : " (" + x.note() + ")")
                    + (x.litresReturned() != null ? ", " + litres(x.litresReturned()) + " returned" : "") + (x.rescheduledTo() != null ? ", rescheduled for " + x.rescheduledTo() : ""), "PROBLEM"));
        }
        if (d.getDeliveredAt() != null) ev.add(new Event(d.getDeliveredAt(), "DELIVERED", "Delivered " + litres(d.getLitresDelivered()) + (blank(d.getReceiverName()) ? "" : ", received by " + d.getReceiverName()) + (d.isSignedOnBehalf() ? " (signed on behalf" + (blank(d.getOnBehalfOf()) ? "" : " of " + d.getOnBehalfOf()) + ")" : ""), "OK"));
        if (d.getReceiptGeneratedAt() != null) ev.add(new Event(d.getReceiptGeneratedAt(), "RECEIPT", "Receipt issued", "OK"));
        if (d.getReversedAt() != null) ev.add(new Event(d.getReversedAt(), "REVERSED", "Reversed" + (blank(d.getReversalReason()) ? "" : ": " + d.getReversalReason()), "PROBLEM"));
        ev.removeIf(e -> e.at() == null);
        ev.sort(Comparator.comparing(Event::at));
        return new Execution(d.getId(), d.getStatus(), d.getDriverName(), d.getVehicleReg(), d.getReceiverName(), d.isSignedOnBehalf(), d.getLitresOrdered(), d.getLoadedLitres(), d.getLitresDelivered(),
                d.getMeterReadingStart(), d.getMeterReadingEnd(), d.getFailedAttempts(), ev);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String litres(BigDecimal l) { return l == null ? "no litres recorded" : l.stripTrailingZeros().toPlainString() + " L"; }
    /** SITE_CLOSED becomes "Site closed". */
    static String label(String code) {
        if (code == null || code.isBlank()) return "Unknown";
        String t = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
