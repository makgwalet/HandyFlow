package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.model.EventPayment;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventPaymentRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static za.co.handyflow.platform.events.application.internal.CsvWriter.*;

/**
 * Payments CSV for finance reconciliation. Contains ticket numbers, amounts, provider references and
 * statuses — no guest names or emails — and every download is audited, like every other export.
 */
@Service
@RequiredArgsConstructor
public class EventPaymentReportService {

    private final EventRepository        eventRepo;
    private final EventPaymentRepository paymentRepo;
    private final EventGuestRepository   guestRepo;
    private final EventAuditService      audit;

    @Transactional
    public EventReportService.Export paymentsCsv(TenantId tenantId, UUID eventId) {
        Event event = eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        int cap = EventReportService.MAX_EXPORT_ROWS;
        List<EventPayment> payments = paymentRepo.search(eventId, null, PageRequest.of(0, cap + 1)).getContent();
        if (payments.size() > cap) {
            throw new HandyFlowException("This report has more than " + cap + " rows — too large for a single export",
                    HttpStatus.CONFLICT, "EXPORT_TOO_LARGE");
        }

        Map<UUID, String> tickets = guestRepo.findAllById(
                        payments.stream().map(EventPayment::getGuestId).distinct().toList()).stream()
                .collect(Collectors.toMap(EventGuest::getId, EventGuest::getTicketNumber, (a, b) -> a));

        CsvWriter w = new CsvWriter();
        w.row("created_at", "ticket_number", "status", "provider", "provider_reference", "amount_due",
                "amount_received", "fee", "refunded", "still_refundable", "currency", "paid_at", "expires_at",
                "failure_reason", "review_reason");
        for (EventPayment p : payments) {
            w.row(time(p.getCreatedAt()), text(tickets.get(p.getGuestId())), text(p.getStatus()),
                    text(p.getProvider()), text(p.getProviderRef()), num(p.getAmount()),
                    num(p.getReceivedAmount()), num(p.getFeeAmount()), num(p.getRefundedAmount()),
                    num(p.refundable()), text(p.getCurrency()), time(p.getPaidAt()), time(p.getExpiresAt()),
                    text(p.getFailureReason()), text(p.getReviewReason()));
        }

        audit.record(tenantId, eventId, "REPORT_EXPORTED", "REPORT", null,
                "Payments exported (" + payments.size() + " row" + (payments.size() == 1 ? "" : "s") + ")");
        return new EventReportService.Export("payments-" + event.getEventNumber() + ".csv",
                "text/csv; charset=UTF-8", w.toBytes());
    }
}