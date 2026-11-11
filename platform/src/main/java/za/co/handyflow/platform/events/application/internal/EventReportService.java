package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.events.dto.CloseOutResponse;
import za.co.handyflow.platform.events.dto.RegistrationDashboardResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

import static za.co.handyflow.platform.events.application.internal.CsvWriter.*;

/**
 * Downloadable reports: CSV exports and the close-out pack PDF.
 * <p>
 * EVERY EXPORT IS AUDITED. Reports that contain personal information (the
 * guest register, check-in log, no-show list) are exactly the data POPIA
 * cares about leaving the system, so each download writes a REPORT_EXPORTED
 * line to the event's audit trail — who, what, how many rows. The methods
 * are therefore @Transactional (read-write) even though they only read event
 * data: the audit entry is the write.
 * <p>
 * SIZE LIMIT: exports are built in memory, so they are refused above
 * {@link #MAX_EXPORT_ROWS} rows rather than risking the application server.
 * Real events sit far below this.
 * <p>
 * CSV cells holding user-supplied text are injection-guarded by CsvWriter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReportService {

    static final int MAX_EXPORT_ROWS = 20_000;
    private static final String CSV = "text/csv; charset=UTF-8";
    private static final String PDF = "application/pdf";

    private final EventRepository           eventRepo;
    private final EventGuestRepository      guestRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventCheckInRepository    checkInRepo;
    private final EventBudgetLineRepository lineRepo;
    private final EventCommitmentRepository commitmentRepo;
    private final EventCostEntryRepository  entryRepo;
    private final EventBudgetService        budgetService;
    private final EventDashboardService     dashboards;
    private final EventCloseOutPdfService   pdfService;
    private final EventAuditService         audit;

    /** A finished download: the file name, its MIME type and the bytes. */
    public record Export(String filename, String contentType, byte[] content) {}

    // ── Guest-level reports (contain personal information) ────────────────────

    /** Every registration record for the event, whatever its status. */
    @Transactional
    public Export guestRegister(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);
        assertWithinLimit(guestRepo.countAllByEvent(eventId));
        Map<UUID, String> tiers = tierNames(eventId);

        CsvWriter w = new CsvWriter();
        w.row("ticket_number", "full_name", "email", "phone", "company", "dietary_requirements", "tier",
                "status", "payment_status", "amount_paid", "source", "registered_at", "checked_in_at");
        List<EventGuest> guests = guestRepo.findAllForExport(eventId);
        for (EventGuest g : guests) {
            w.row(text(g.getTicketNumber()), text(g.getFullName()), text(g.getEmail()), text(g.getPhone()),
                    text(g.getCompany()), text(g.getDietaryRequirements()),
                    text(g.getTierId() != null ? tiers.getOrDefault(g.getTierId(), "") : ""),
                    text(g.getStatus()), text(g.getPaymentStatus()), num(g.getAmountPaid()),
                    text(g.getSource()), time(g.getCreatedAt()), time(g.getCheckedInAt()));
        }
        return finish(tenantId, event, w, "guest-register", guests.size(), "Guest register");
    }

    /**
     * Registered guests who were never scanned in. Only meaningful once the
     * event has started, so it is refused for DRAFT/PUBLISHED/SOLD_OUT events
     * (where everyone would trivially appear).
     */
    @Transactional
    public Export noShows(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);
        if (!"LIVE".equals(event.getStatus()) && !"COMPLETED".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "A no-show list is only available once the event is LIVE or COMPLETED (currently " + event.getStatus() + ")",
                    HttpStatus.CONFLICT, "EVENT_NOT_STARTED");
        }
        assertWithinLimit(guestRepo.countAllByEvent(eventId));
        Map<UUID, String> tiers = tierNames(eventId);

        CsvWriter w = new CsvWriter();
        w.row("ticket_number", "full_name", "email", "phone", "company", "tier", "status", "payment_status",
                "source", "registered_at");
        List<EventGuest> guests = guestRepo.findNotCheckedIn(eventId);
        for (EventGuest g : guests) {
            w.row(text(g.getTicketNumber()), text(g.getFullName()), text(g.getEmail()), text(g.getPhone()),
                    text(g.getCompany()), text(g.getTierId() != null ? tiers.getOrDefault(g.getTierId(), "") : ""),
                    text(g.getStatus()), text(g.getPaymentStatus()), text(g.getSource()), time(g.getCreatedAt()));
        }
        return finish(tenantId, event, w, "no-shows", guests.size(), "No-show list");
    }

    /**
     * Every scan, successful or not, oldest first — including repeated scans,
     * cancelled and transferred tickets, and tickets that weren't found. This
     * is the evidence trail for door disputes and for spotting ticket fraud.
     */
    @Transactional
    public Export checkInLog(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);
        assertWithinLimit(checkInRepo.countByEvent(eventId));

        Map<UUID, EventGuest> guestsById = guestRepo.findAllForExport(eventId).stream()
                .collect(Collectors.toMap(EventGuest::getId, g -> g, (a, b) -> a));

        List<EventCheckIn> scans = new ArrayList<>(checkInRepo.findByEvent(eventId));
        Collections.reverse(scans);   // repository returns newest first; an audit log reads oldest first

        CsvWriter w = new CsvWriter();
        w.row("scanned_at", "result", "ticket_number", "guest_name", "scanned_by_user_id", "device", "location");
        for (EventCheckIn c : scans) {
            EventGuest g = c.getGuestId() != null ? guestsById.get(c.getGuestId()) : null;
            w.row(time(c.getScannedAt()), text(c.getResult()),
                    text(g != null ? g.getTicketNumber() : null), text(g != null ? g.getFullName() : null),
                    text(c.getScannedBy() != null ? c.getScannedBy().toString() : null),
                    text(c.getScanDevice()), text(c.getLocation()));
        }
        return finish(tenantId, event, w, "check-in-log", scans.size(), "Check-in log");
    }

    // ── Financial reports (no personal information) ───────────────────────────

    /** Budget vs actual, one row per line plus a total. */
    @Transactional
    public Export budgetVsActual(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);
        BudgetSummaryResponse s = budgetService.getSummary(tenantId, eventId);

        CsvWriter w = new CsvWriter();
        w.row("category", "description", "planned", "committed", "invoiced", "paid", "forecast",
                "variance", "over_budget");
        for (BudgetSummaryResponse.LineSummary l : s.lines()) {
            w.row(text(l.category()), text(l.description()), num(l.planned()), num(l.committed()),
                    num(l.invoiced()), num(l.paid()), num(l.forecast()), num(l.variance()), bool(l.overBudget()));
        }
        w.row(text("TOTAL"), "", num(s.planned()), num(s.committed()), num(s.invoiced()), num(s.paid()),
                num(s.forecast()), num(s.variance()), "");
        return finish(tenantId, event, w, "budget-vs-actual", s.lines().size(), "Budget vs actual");
    }

    /** Every invoice and payment, voided ones included and marked, so the export shows the full history. */
    @Transactional
    public Export costEntries(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);

        Map<UUID, String> lineCategory = lineRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventBudgetLine::getId, EventBudgetLine::getCategory));
        Map<UUID, String> commitmentDescription = commitmentRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventCommitment::getId, EventCommitment::getDescription));

        List<EventCostEntry> entries = entryRepo
                .search(eventId, null, null, null, null, PageRequest.of(0, MAX_EXPORT_ROWS + 1))
                .getContent();
        assertWithinLimit(entries.size());

        CsvWriter w = new CsvWriter();
        w.row("entry_date", "kind", "deposit", "amount", "budget_category", "commitment", "reference",
                "description", "status", "void_reason", "linked_module", "linked_reference", "recorded_by", "recorded_at");
        for (EventCostEntry e : entries) {
            w.row(date(e.getEntryDate()), text(e.getKind()), bool(e.isDeposit()), num(e.getAmount()),
                    text(lineCategory.get(e.getBudgetLineId())),
                    text(e.getCommitmentId() != null ? commitmentDescription.get(e.getCommitmentId()) : null),
                    text(e.getReference()), text(e.getDescription()), text(e.getStatus()),
                    text(e.getVoidReason()), text(e.getSourceModule()), text(e.getSourceRef()),
                    text(e.getRecordedByName()), time(e.getCreatedAt()));
        }
        return finish(tenantId, event, w, "cost-entries", entries.size(), "Cost entries");
    }

    // ── Close-out pack ────────────────────────────────────────────────────────

    @Transactional
    public Export closeOutPack(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);
        CloseOutResponse closeOut = dashboards.getCloseOut(tenantId, eventId);
        RegistrationDashboardResponse registration = dashboards.getRegistration(tenantId, eventId);

        byte[] pdf = pdfService.generate(event, registration, closeOut);
        audit.record(tenantId, eventId, "REPORT_EXPORTED", "REPORT", null,
                "Close-out pack generated (" + ("COMPLETED".equals(event.getStatus()) ? "final" : "interim") + ")");
        log.info("[Events] Close-out pack generated event={}", event.getEventNumber());
        return new Export("close-out-pack-" + event.getEventNumber() + ".pdf", PDF, pdf);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * The single exit for every CSV report: writes the audit line, then packages the
     * file. Called last, after the rows are built, so a failure while building the
     * report never leaves an audit entry claiming a download that didn't happen.
     */
    private Export finish(TenantId tenantId, Event event, CsvWriter w, String reportName, int rows, String label) {
        audit.record(tenantId, event.getId(), "REPORT_EXPORTED", "REPORT", null,
                label + " exported (" + rows + " row" + (rows == 1 ? "" : "s") + ")");
        log.info("[Events] Report exported report={} event={} rows={}", reportName, event.getEventNumber(), rows);
        return new Export(reportName + "-" + event.getEventNumber() + ".csv", CSV, w.toBytes());
    }

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Map<UUID, String> tierNames(UUID eventId) {
        return tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));
    }

    private void assertWithinLimit(long rows) {
        if (rows > MAX_EXPORT_ROWS) {
            throw new HandyFlowException(
                    "This report has " + rows + " rows — the maximum for a single export is " + MAX_EXPORT_ROWS,
                    HttpStatus.CONFLICT, "EXPORT_TOO_LARGE");
        }
    }
}