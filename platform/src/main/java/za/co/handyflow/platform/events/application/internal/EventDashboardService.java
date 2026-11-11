package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.events.dto.AttendanceDashboardResponse.TierAttendance;
import za.co.handyflow.platform.events.dto.AttendanceDashboardResponse.TimeBucket;
import za.co.handyflow.platform.events.dto.CloseOutResponse.AttendanceSummary;
import za.co.handyflow.platform.events.dto.CloseOutResponse.CloseOutCheck;
import za.co.handyflow.platform.events.dto.EventsOverviewResponse.AttentionItem;
import za.co.handyflow.platform.events.dto.EventsOverviewResponse.UpcomingEvent;
import za.co.handyflow.platform.events.dto.RegistrationDashboardResponse.DayCount;
import za.co.handyflow.platform.events.dto.RegistrationDashboardResponse.TierRow;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only dashboards. Counts come from database GROUP BY queries rather
 * than loading guest lists, so a dashboard over a 5,000-guest event costs a
 * handful of cheap aggregate queries, and the attendance dashboard is light
 * enough to poll every few seconds from the door.
 * <p>
 * Nothing here writes. Every figure is derived from the authoritative event,
 * guest, check-in, budget and cost rows at the moment of the request — no
 * cached totals that could drift out of step with them. The budget figures
 * come from the same BudgetCalculator the budget screens use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDashboardService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final List<String> STATUS_ORDER =
            List.of("DRAFT", "PUBLISHED", "SOLD_OUT", "LIVE", "COMPLETED", "CANCELLED");

    private static final int OVERVIEW_LOOKBACK_DAYS = 7;
    private static final int OVERVIEW_WINDOW_DAYS = 60;
    private static final int OVERVIEW_MAX_EVENTS = 50;
    private static final long QUARTER_HOUR_SECONDS = 15 * 60;

    // Attention thresholds
    private static final int DRAFT_WARNING_DAYS = 14;
    private static final int BUDGET_WARNING_DAYS = 30;
    private static final int LOW_REGISTRATION_DAYS = 14;
    private static final BigDecimal LOW_FILL_PERCENT = new BigDecimal("30");
    private static final BigDecimal NEAR_CAPACITY_PERCENT = new BigDecimal("90");
    private static final int DEADLINE_WARNING_DAYS = 3;

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventCheckInRepository    checkInRepo;
    private final EventWaitlistRepository   waitlistRepo;
    private final EventBudgetRepository     budgetRepo;
    private final EventBudgetLineRepository lineRepo;
    private final EventCommitmentRepository commitmentRepo;
    private final EventCostEntryRepository  entryRepo;
    private final EventVendorRepository     vendorRepo;
    private final EventPaymentRepository    paymentRepo;
    private final EventRefundRepository     refundRepo;

    // ── Overview (tenant-wide) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EventsOverviewResponse getOverview(TenantId tenantId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        STATUS_ORDER.forEach(s -> counts.put(s, 0L));
        for (LabelCount lc : eventRepo.countByStatusForTenant(tenantId)) {
            counts.put(lc.getLabel(), lc.getTotal());
        }

        LocalDateTime now = LocalDateTime.now(SAST);
        List<Event> events = eventRepo.findUpcomingOrLive(tenantId,
                now.minusDays(OVERVIEW_LOOKBACK_DAYS), now.plusDays(OVERVIEW_WINDOW_DAYS),
                PageRequest.of(0, OVERVIEW_MAX_EVENTS));

        Map<UUID, Long> registered = new HashMap<>();
        Map<UUID, String> budgetStatus = new HashMap<>();
        if (!events.isEmpty()) {
            List<UUID> ids = events.stream().map(Event::getId).toList();
            for (EventCount ec : guestRepo.countActiveByEvents(ids)) registered.put(ec.getEventId(), ec.getTotal());
            for (EventLabel el : budgetRepo.statusesForEvents(ids)) budgetStatus.put(el.getEventId(), el.getLabel());
        }

        List<UpcomingEvent> upcoming = new ArrayList<>();
        List<AttentionItem> attention = new ArrayList<>();
        for (Event e : events) {
            long reg = registered.getOrDefault(e.getId(), 0L);
            BigDecimal fill = fillPercent(reg, e.getVenueCapacity());
            long days = ChronoUnit.DAYS.between(now.toLocalDate(), e.getStartDatetime().toLocalDate());
            String budget = budgetStatus.get(e.getId());

            upcoming.add(new UpcomingEvent(e.getId(), e.getEventNumber(), e.getTitle(), e.getStatus(),
                    e.getStartDatetime(), days, e.getVenueCapacity(), reg, fill, budget));
            attention.addAll(attentionFor(e, now, days, fill, budget));
        }
        // List.sort is stable: warnings first, original (start-date) order kept within each severity.
        attention.sort(Comparator.comparingInt(a -> "WARNING".equals(a.severity()) ? 0 : 1));

        return new EventsOverviewResponse(counts, upcoming, attention);
    }

    private List<AttentionItem> attentionFor(Event e, LocalDateTime now, long days, BigDecimal fill, String budgetStatus) {
        List<AttentionItem> out = new ArrayList<>();
        String label = e.getEventNumber() + " " + e.getTitle();
        String status = e.getStatus();
        boolean preLive = List.of("DRAFT", "PUBLISHED", "SOLD_OUT").contains(status);

        if ("DRAFT".equals(status) && days <= DRAFT_WARNING_DAYS) {
            out.add(item("DRAFT_STARTING_SOON", "WARNING", e, days < 0
                    ? label + " is still a DRAFT and its start date has passed"
                    : label + " is still a DRAFT and starts " + inDays(days)));
        }
        if (("PUBLISHED".equals(status) || "SOLD_OUT".equals(status)) && e.getStartDatetime().isBefore(now)) {
            out.add(item("NOT_LIVE_ON_TIME", "WARNING", e,
                    label + " was due to start " + e.getStartDatetime().toLocalDate() + " but hasn't gone live"));
        }
        if (preLive && days >= 0 && days <= BUDGET_WARNING_DAYS
                && budgetStatus != null && !"APPROVED".equals(budgetStatus)) {
            boolean submitted = "SUBMITTED".equals(budgetStatus);
            out.add(item("BUDGET_NOT_APPROVED", submitted ? "WARNING" : "INFO", e,
                    label + " — budget is " + (submitted ? "awaiting approval" : "still a draft")
                            + " and the event starts " + inDays(days)));
        }
        if ("PUBLISHED".equals(status) && fill != null && days >= 0 && days <= LOW_REGISTRATION_DAYS
                && fill.compareTo(LOW_FILL_PERCENT) < 0) {
            out.add(item("LOW_REGISTRATION", "WARNING", e,
                    label + " is only " + fill.stripTrailingZeros().toPlainString() + "% full and starts " + inDays(days)));
        }
        LocalDateTime deadline = e.getRegistrationDeadline();
        if ("PUBLISHED".equals(status) && deadline != null && !deadline.isBefore(now)
                && deadline.isBefore(now.plusDays(DEADLINE_WARNING_DAYS))) {
            out.add(item("REGISTRATION_CLOSING", "INFO", e,
                    "Registration for " + label + " closes " + deadline.toLocalDate()));
        }
        if ("PUBLISHED".equals(status) && fill != null && fill.compareTo(NEAR_CAPACITY_PERCENT) >= 0) {
            out.add(item("NEAR_CAPACITY", "INFO", e,
                    label + " is " + fill.stripTrailingZeros().toPlainString() + "% full"));
        }
        return out;
    }

    // ── Registration dashboard ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RegistrationDashboardResponse getRegistration(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);

        Map<String, Long> byStatus = labelMap(guestRepo.countGroupedByStatus(eventId));
        Map<String, Long> bySource = labelMap(guestRepo.countActiveGroupedBySource(eventId));
        long registered = guestRepo.countActive(eventId);
        Integer capacity = event.getVenueCapacity();

        List<TierRow> tiers = tierRepo.findByEvent(eventId).stream()
                .map(t -> new TierRow(t.getId(), t.getName(), t.getPrice(), t.getQuantity(),
                        t.getQuantitySold(), t.getAvailable(), t.getQuantityCheckedIn(),
                        t.getPrice().multiply(BigDecimal.valueOf(t.getQuantitySold()))))
                .toList();

        BigDecimal recorded = guestRepo.sumAmountPaid(eventId);
        long pending = event.isFree() ? 0 : guestRepo.countPaymentPending(eventId);

        return new RegistrationDashboardResponse(eventId, event.getEventNumber(), event.getTitle(),
                event.getStatus(), registered, capacity, fillPercent(registered, capacity),
                capacity == null ? null : Math.max(0L, capacity - registered),
                waitlistRepo.countWaiting(eventId),
                byStatus.getOrDefault("CANCELLED", 0L), byStatus.getOrDefault("TRANSFERRED", 0L),
                byStatus.getOrDefault("CHECKED_IN", 0L),
                byStatus, bySource, tiers, registrationsByDay(guestRepo.findRegistrationTimes(eventId)),
                recorded != null ? recorded : BigDecimal.ZERO.setScale(2), pending);
    }

    private List<DayCount> registrationsByDay(List<Instant> times) {
        TreeMap<LocalDate, Long> perDay = new TreeMap<>();
        for (Instant t : times) perDay.merge(LocalDate.ofInstant(t, SAST), 1L, Long::sum);

        List<DayCount> out = new ArrayList<>();
        long cumulative = 0;
        for (Map.Entry<LocalDate, Long> e : perDay.entrySet()) {
            cumulative += e.getValue();
            out.add(new DayCount(e.getKey(), e.getValue(), cumulative));
        }
        return out;
    }

    // ── Attendance / command-centre dashboard ─────────────────────────────────

    @Transactional(readOnly = true)
    public AttendanceDashboardResponse getAttendance(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);

        // Unpaid RESERVED holds occupy a place but aren't expected at the door until payment is confirmed.
        long expected = guestRepo.countActive(eventId) - guestRepo.countByStatus(eventId, "RESERVED");
        long checkedIn = guestRepo.countCheckedIn(eventId);
        Map<String, Long> scanResults = labelMap(checkInRepo.countGroupedByResult(eventId));

        List<TierAttendance> byTier = tierRepo.findByEvent(eventId).stream()
                .map(t -> new TierAttendance(t.getId(), t.getName(), t.getQuantitySold(), t.getQuantityCheckedIn()))
                .toList();

        List<Instant> admissions = checkInRepo.findAdmissionTimes(eventId);
        TreeMap<Instant, Long> buckets = new TreeMap<>();
        for (Instant t : admissions) {
            Instant start = Instant.ofEpochSecond((t.getEpochSecond() / QUARTER_HOUR_SECONDS) * QUARTER_HOUR_SECONDS);
            buckets.merge(start, 1L, Long::sum);
        }
        List<TimeBucket> arrivals = buckets.entrySet().stream()
                .map(e -> new TimeBucket(e.getKey(), e.getValue())).toList();

        return new AttendanceDashboardResponse(eventId, event.getEventNumber(), event.getTitle(),
                event.getStatus(), expected, checkedIn, Math.max(0L, expected - checkedIn),
                percent(checkedIn, expected), scanResults.getOrDefault("WALK_IN", 0L),
                scanResults, byTier, arrivals,
                admissions.isEmpty() ? null : admissions.get(0),
                admissions.isEmpty() ? null : admissions.get(admissions.size() - 1),
                Instant.now());
    }

    // ── Close-out ─────────────────────────────────────────────────────────────

    /**
     * Attendance reconciliation, the final cost position, and a checklist of what
     * is still open. The checklist is advisory — it never blocks completing an
     * event — but readyToClose is true only when every check passes.
     */
    @Transactional(readOnly = true)
    public CloseOutResponse getCloseOut(TenantId tenantId, UUID eventId) {
        Event event = findEvent(tenantId, eventId);

        Map<String, Long> byStatus = labelMap(guestRepo.countGroupedByStatus(eventId));
        long registered = guestRepo.countActive(eventId) - byStatus.getOrDefault("RESERVED", 0L);   // paid / issued tickets only
        long checkedIn = guestRepo.countCheckedIn(eventId);
        long notCheckedIn = byStatus.getOrDefault("REGISTERED", 0L) + byStatus.getOrDefault("CONFIRMED", 0L);
        long walkIns = labelMap(checkInRepo.countGroupedByResult(eventId)).getOrDefault("WALK_IN", 0L);

        AttendanceSummary attendance = new AttendanceSummary(registered, checkedIn, notCheckedIn, walkIns,
                byStatus.getOrDefault("CANCELLED", 0L), byStatus.getOrDefault("TRANSFERRED", 0L),
                percent(checkedIn, registered));

        List<EventBudgetLine> lines = lineRepo.findByEvent(eventId);
        List<EventCommitment> commitments = commitmentRepo.findByEvent(eventId);
        List<EventCostEntry> entries = entryRepo.findActiveByEvent(eventId);
        String budgetStatus = budgetRepo.findByEvent(eventId).map(EventBudget::getStatus).orElse("NOT_STARTED");

        BudgetSummaryResponse budget = BudgetCalculator.summarize(budgetStatus, lines, commitments, entries,
                LocalDate.now(SAST), registered, checkedIn);
        List<SupplierSummary> suppliers = BudgetCalculator.bySupplier(commitments, entries, vendorNames(eventId));

        List<CloseOutCheck> checks = new ArrayList<>();

        boolean completed = "COMPLETED".equals(event.getStatus());
        checks.add(new CloseOutCheck("EVENT_COMPLETED", "Event marked completed", completed,
                completed ? "Completed" : "Status is " + event.getStatus()));

        boolean budgetOk = "NOT_STARTED".equals(budgetStatus) || "APPROVED".equals(budgetStatus);
        checks.add(new CloseOutCheck("BUDGET_APPROVED", "Budget approved (or none recorded)", budgetOk,
                "NOT_STARTED".equals(budgetStatus) ? "No budget recorded" : "Budget is " + budgetStatus));

        long openCommitments = commitments.stream()
                .filter(c -> EventCommitment.QUOTED.equals(c.getStatus()) || EventCommitment.APPROVED.equals(c.getStatus()))
                .count();
        checks.add(new CloseOutCheck("COMMITMENTS_CLOSED",
                "Every supplier quote and commitment completed or cancelled", openCommitments == 0,
                openCommitments == 0 ? "None open" : openCommitments + " still open"));

        boolean settled = budget.outstanding().signum() == 0;
        checks.add(new CloseOutCheck("BALANCES_SETTLED", "No outstanding supplier balances", settled,
                settled ? "Nothing outstanding" : money(budget.outstanding()) + " still to pay"));

        long unreconciled = entries.stream().filter(e -> !e.isReconciled()).count();
        checks.add(new CloseOutCheck("COSTS_RECONCILED", "Every cost entry linked to an accounting record",
                unreconciled == 0,
                unreconciled == 0 ? "All linked" : unreconciled + " not linked"));

        long overLines = budget.lines().stream().filter(BudgetSummaryResponse.LineSummary::overBudget).count();
        checks.add(new CloseOutCheck("WITHIN_BUDGET", "No budget line over its planned amount", overLines == 0,
                overLines == 0 ? "All lines within budget" : overLines + " line(s) over budget"));

        long pendingPayments = event.isFree() ? 0 : guestRepo.countPaymentPending(eventId);
        checks.add(new CloseOutCheck("GUEST_PAYMENTS_SETTLED", "No guest payments still pending",
                pendingPayments == 0,
                pendingPayments == 0 ? "None pending" : pendingPayments + " guest(s) still PENDING"));

        long openCheckouts = paymentRepo.countPending(eventId);
        long refundsOwed = paymentRepo.countRefundsOwed(eventId);
        boolean paymentsClear = openCheckouts == 0 && refundsOwed == 0;
        checks.add(new CloseOutCheck("PAYMENTS_RESOLVED", "No open checkouts and no refunds still owed", paymentsClear,
                paymentsClear ? "All payments resolved"
                        : openCheckouts + " open checkout(s), " + refundsOwed + " payment(s) with a refund owed"));

        long refundsInFlight = refundRepo.countRequested(eventId);
        checks.add(new CloseOutCheck("REFUNDS_COMPLETED", "Every requested refund completed or failed",
                refundsInFlight == 0,
                refundsInFlight == 0 ? "None in flight" : refundsInFlight + " refund(s) still REQUESTED"));

        boolean ready = checks.stream().allMatch(CloseOutCheck::passed);
        return new CloseOutResponse(eventId, event.getEventNumber(), event.getTitle(), event.getStatus(),
                attendance, budget, suppliers, checks, ready, Instant.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Map<UUID, String> vendorNames(UUID eventId) {
        return vendorRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventVendor::getId, EventVendor::getCompanyName, (a, b) -> a));
    }

    private static Map<String, Long> labelMap(List<LabelCount> rows) {
        Map<String, Long> map = new TreeMap<>();
        for (LabelCount r : rows) map.put(r.getLabel(), r.getTotal() == null ? 0L : r.getTotal());
        return map;
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole <= 0) return null;
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal fillPercent(long registered, Integer capacity) {
        return (capacity == null || capacity <= 0) ? null : percent(registered, capacity);
    }

    private static String inDays(long days) {
        return days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
    }

    private static AttentionItem item(String type, String severity, Event e, String message) {
        return new AttentionItem(type, severity, message, e.getId(), e.getEventNumber());
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}