package za.co.handyflow.platform.bookings.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.domain.model.*;
import za.co.handyflow.platform.bookings.domain.repository.*;
import za.co.handyflow.platform.bookings.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * BookingsService — application-layer orchestrator for the Bookings module.
 *
 * FIXES APPLIED VS ORIGINAL:
 *
 * 1. N+1 query in getBookings()
 *    Original: called serviceRepo + staffRepo inside the JDBC RowMapper
 *    for every row.  50 bookings = 100 extra queries.
 *    Fixed: JOIN in SQL, resolve names directly from result set.
 *
 * 2. TOCTOU race in createBooking()
 *    Original: findConflicts() check → then INSERT (gap between).
 *    Fixed: still check upfront for a friendly error, but the DB-level
 *    unique index (V97) provides the hard guarantee.  DataIntegrityViolation
 *    is caught and converted to ConflictException so the caller gets
 *    a 409 instead of a 500.
 *
 * 3. Booking number race condition
 *    Fixed in BookingNumberGenerator via atomic INSERT ON CONFLICT.
 *
 * 4. Buffer time not respected in createBooking()
 *    Fixed: effective slot end = endTime + bufferAfterMinutes is used
 *    for conflict checking so the buffer is enforced at write time.
 *
 * 5. No lead time / advance booking validation
 *    Fixed: minLeadTimeMinutes and maxAdvanceDays checked in createBooking.
 *
 * 6. No reschedule action
 *    Fixed: rescheduleBooking() moves the booking, preserves history.
 *
 * 7. Non-atomic availability upsert
 *    Fixed: delete + insert wrapped in same @Transactional — already the
 *    case due to @Transactional on the method, but now explicitly noted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingsService {

    private final BookingRepository             bookingRepo;
    private final BookingServiceRepository      serviceRepo;
    private final BookingStaffRepository        staffRepo;
    private final BookingAvailabilityRepository availabilityRepo;
    private final BookingBlockRepository        blockRepo;
    private final BookingNumberGenerator        numberGen;
    private final SlotEngine                    slotEngine;
    private final EmailService                  emailService;
    private final BookingConfirmationPdfService  confirmationPdfService;
    private final BookingServiceStaffRepository  serviceStaffRepo;
    private final JdbcTemplate                  jdbc;

    // ── Services ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ServiceResponse> getServices(TenantId tenantId) {
        return serviceRepo.findAllActive(tenantId)
                .stream().map(this::toServiceResponse).toList();
    }

    @Transactional
    public ServiceResponse createService(TenantId tenantId, CreateServiceRequest req) {
        BookingService s = BookingService.create(tenantId, req.name(),
                req.description(), req.durationMinutes(), req.price(), req.color(),
                req.bufferBeforeMinutes(), req.bufferAfterMinutes(),
                req.minLeadTimeMinutes(), req.maxAdvanceDays());
        serviceRepo.save(s);
        log.info("Created booking service={} tenant={}", s.getName(), tenantId);
        return toServiceResponse(s);
    }

    @Transactional
    public ServiceResponse updateService(TenantId tenantId, UUID id,
                                         CreateServiceRequest req) {
        BookingService s = serviceRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingService", id.toString()));
        s.update(req.name(), req.description(), req.durationMinutes(),
                req.price(), req.color(),
                req.bufferBeforeMinutes(), req.bufferAfterMinutes(),
                req.minLeadTimeMinutes(), req.maxAdvanceDays());
        serviceRepo.save(s);
        return toServiceResponse(s);
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaffResponse> getStaff(TenantId tenantId) {
        return staffRepo.findAllActive(tenantId)
                .stream().map(this::toStaffResponse).toList();
    }

    @Transactional
    public StaffResponse createStaff(TenantId tenantId, CreateStaffRequest req) {
        BookingStaff s = BookingStaff.create(tenantId, req.name(),
                req.email(), req.phone(), req.employeeId());
        staffRepo.save(s);
        return toStaffResponse(s);
    }

    // ── Availability ──────────────────────────────────────────────────────────

    @Transactional
    public void setAvailability(TenantId tenantId, SetAvailabilityRequest req) {
        // Delete + insert in same @Transactional — atomic at the transaction boundary.
        // WHY not UPSERT here? The JPA entity has a surrogate UUID PK, so we can't
        // do ON CONFLICT (tenant_id, staff_id, day_of_week) easily without native SQL.
        // The delete+insert pattern is correct as long as it's inside one transaction.
        availabilityRepo.findForStaff(tenantId, req.staffId()).stream()
                .filter(a -> a.getDayOfWeek() == req.dayOfWeek())
                .forEach(availabilityRepo::delete);

        BookingAvailability a = BookingAvailability.create(tenantId,
                req.staffId(), req.dayOfWeek(), req.startTime(), req.endTime());
        availabilityRepo.save(a);
        log.info("Set availability dow={} {}–{} staff={} tenant={}",
                req.dayOfWeek(), req.startTime(), req.endTime(),
                req.staffId(), tenantId);
    }

    @Transactional
    public void addBlock(TenantId tenantId, AddBlockRequest req) {
        BookingBlock b = BookingBlock.create(tenantId, req.staffId(),
                req.blockDate(), req.startTime(), req.endTime(), req.reason());
        blockRepo.save(b);
    }

    // ── Available slots ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AvailableSlot> getAvailableSlots(TenantId tenantId, UUID serviceId,
                                                 LocalDate date, UUID staffId) {
        BookingService service = serviceRepo.findActiveById(tenantId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingService",
                        serviceId.toString()));

        // Lead time check — don't offer slots that are too soon
        // Even for the availability view, we suppress slots the service
        // can't accept.  The client sees only genuinely bookable slots.
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return List.of();  // past dates never bookable

        // Skill check — if this service has staff assignments, only show slots
        // for staff members who are assigned to it.  If no assignments exist,
        // all staff are eligible (backwards-compatible default).
        if (staffId != null && serviceStaffRepo.hasAnyAssignments(serviceId)) {
            if (!serviceStaffRepo.existsAssignment(serviceId, staffId)) {
                log.debug("Staff {} not assigned to service {} — returning no slots", staffId, serviceId);
                return List.of();
            }
        }

        // Max advance days check — suppress the entire date if it's beyond the service window.
        // WHY here AND in createBooking?
        // createBooking guards against direct API calls bypassing the slot UI.
        // This guard stops the slot engine from returning slots for a date that
        // the service doesn't allow — the UI date picker would show "no slots"
        // rather than silently letting the user pick a disallowed date.
        if (date.isAfter(LocalDate.now().plusDays(service.getMaxAdvanceDays()))) {
            return List.of();
        }

        return slotEngine.getAvailableSlots(tenantId, staffId, date,
                service.getDurationMinutes(),
                service.getBufferBeforeMinutes(),
                service.getBufferAfterMinutes(),
                service.getMinLeadTimeMinutes());
    }

    // ── Bookings ──────────────────────────────────────────────────────────────

    /**
     * List bookings with optional filters.
     *
     * WHY JdbcTemplate with a JOIN instead of JPA?
     * The original used JPA for the list query but then called serviceRepo
     * and staffRepo inside the RowMapper — once per row.  50 bookings = 100
     * extra queries.  This is the classic N+1 problem.
     *
     * The fix: JOIN booking_services and booking_staff directly in SQL so
     * names are resolved in one query.  JdbcTemplate is the right tool here
     * because we need a dynamic WHERE clause (status/date/staffId filters)
     * which is awkward to compose in JPQL.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(TenantId tenantId, String status,
                                             LocalDate date, LocalDate dateFrom,
                                             LocalDate dateTo, UUID staffId,
                                             String search, Pageable pageable) {
        // JOIN resolves service name + staff name in a single query — no N+1
        String baseSelect = """
                SELECT b.*,
                       bs.name  AS service_name,
                       bst.name AS staff_name
                FROM bookings b
                LEFT JOIN booking_services bs  ON bs.id  = b.service_id
                LEFT JOIN booking_staff    bst ON bst.id = b.staff_id
                WHERE b.tenant_id = ?
                """;

        List<Object> params = new ArrayList<>();
        params.add(tenantId.getValue());

        StringBuilder where = new StringBuilder();
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ?");
            params.add(status);
        }
        // Exact date filter (single day — BookingsTab date picker)
        if (date != null) {
            where.append(" AND b.booking_date = ?");
            params.add(date);
        }
        // Date range filter (calendar week view — dateFrom+dateTo, mutually exclusive with date)
        // WHY two separate params instead of one interval?
        // The frontend sends the Monday and Sunday of the visible week.
        // BETWEEN is inclusive on both ends, which is correct: a booking on
        // Sunday must appear in the Sunday column of the calendar.
        if (date == null && dateFrom != null) {
            where.append(" AND b.booking_date >= ?");
            params.add(dateFrom);
        }
        if (date == null && dateTo != null) {
            where.append(" AND b.booking_date <= ?");
            params.add(dateTo);
        }
        if (staffId != null) {
            where.append(" AND b.staff_id = ?");
            params.add(staffId);
        }
        if (search != null && !search.isBlank()) {
            // WHY ILIKE? Case-insensitive — works without pg_trgm.
            // Covers partial name ("Tha" matches "Thabo") and
            // phone prefix ("+27 82" matches any +27 82 number).
            where.append(" AND (b.client_name ILIKE ? OR b.client_phone ILIKE ? OR b.client_email ILIKE ?)");
            String term = "%" + search.strip() + "%";
            params.add(term); params.add(term); params.add(term);
        }

        String countSql = "SELECT COUNT(*) FROM bookings b WHERE b.tenant_id = ?"
                + where;
        String pageSql  = baseSelect + where
                + " ORDER BY b.booking_date DESC, b.start_time ASC LIMIT ? OFFSET ?";

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageable.getPageSize());
        pageParams.add(pageable.getOffset());

        List<BookingResponse> rows = jdbc.query(pageSql,
                (rs, n) -> mapBookingResponse(rs),
                pageParams.toArray());

        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        return new PageImpl<>(rows, pageable, total != null ? total : 0L);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(TenantId tenantId, UUID id) {
        // Single-row fetch: JOIN is worth it even here for consistency
        List<BookingResponse> rows = jdbc.query("""
                SELECT b.*,
                       bs.name  AS service_name,
                       bst.name AS staff_name
                FROM bookings b
                LEFT JOIN booking_services bs  ON bs.id  = b.service_id
                LEFT JOIN booking_staff    bst ON bst.id = b.staff_id
                WHERE b.tenant_id = ? AND b.id = ?
                """,
                (rs, n) -> mapBookingResponse(rs),
                tenantId.getValue(), id);

        if (rows.isEmpty())
            throw new ResourceNotFoundException("Booking", id.toString());
        return rows.get(0);
    }

    @Transactional
    public BookingResponse createBooking(TenantId tenantId, CreateBookingRequest req) {
        BookingService service = serviceRepo.findActiveById(tenantId, req.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("BookingService",
                        req.serviceId().toString()));

        LocalDate today     = LocalDate.now();
        LocalTime slotEnd   = req.startTime().plusMinutes(service.getDurationMinutes());

        // ── Lead time validation ──────────────────────────────────────────────
        // WHY check this in the service and not just the slot engine?
        // The slot engine filters available slots for the booking UI.
        // But createBooking can be called directly (API, imports, integrations)
        // without going through getAvailableSlots.  We validate here so the
        // rule is enforced regardless of how the endpoint is reached.
        if (service.getMinLeadTimeMinutes() > 0 && req.bookingDate().equals(today)) {
            LocalTime cutoff = LocalTime.now().plusMinutes(service.getMinLeadTimeMinutes());
            if (req.startTime().isBefore(cutoff)) {
                throw new ConflictException(
                        "Bookings for '" + service.getName() + "' must be made at least "
                                + service.getMinLeadTimeMinutes() + " minutes in advance. "
                                + "Earliest bookable time today: " + cutoff.withSecond(0).withNano(0)
                );
            }
        }

        // ── Max advance validation ────────────────────────────────────────────
        if (req.bookingDate().isAfter(today.plusDays(service.getMaxAdvanceDays()))) {
            throw new ConflictException(
                    "Bookings for '" + service.getName() + "' can only be made up to "
                            + service.getMaxAdvanceDays() + " days in advance."
            );
        }

        // ── Conflict check (service layer — friendly error) ───────────────────
        // WHY check both the service layer AND rely on the DB constraint?
        // The service layer check gives a user-friendly error message.
        // The DB constraint (V97 unique index) is the hard guarantee against races.
        // Defence in depth: friendly message for the 99% case, hard constraint
        // for the rare concurrent-request race.
        if (req.staffId() != null) {
            // Buffer: extend slotEnd by bufferAfterMinutes for conflict detection
            LocalTime effectiveEnd = slotEnd.plusMinutes(service.getBufferAfterMinutes());
            var conflicts = bookingRepo.findConflicts(
                    req.staffId(), req.bookingDate(), req.startTime(), effectiveEnd);
            if (!conflicts.isEmpty()) {
                throw new ConflictException(
                        "This time slot is no longer available. Please choose another slot.");
            }
        }

        // ── Create the booking ────────────────────────────────────────────────
        String number = numberGen.next(tenantId);
        Booking booking = Booking.create(tenantId, number, req.serviceId(),
                req.staffId(), req.customerId(), req.clientName(), req.clientEmail(),
                req.clientPhone(), req.bookingDate(), req.startTime(),
                service.getDurationMinutes(), service.getPrice(), req.notes());

        try {
            bookingRepo.save(booking);
        } catch (DataIntegrityViolationException ex) {
            // DB unique index fired — concurrent booking beat us to this slot
            // Convert the constraint violation to a friendly 409
            throw new ConflictException(
                    "This time slot was just taken. Please select another slot.");
        }

        log.info("Created booking={} client={} date={} service={}",
                number, req.clientName(), req.bookingDate(), service.getName());

        // Send confirmation email to client (async — failure does not roll back booking)
        if (req.clientEmail() != null && !req.clientEmail().isBlank()) {
            emailService.send(
                    req.clientEmail(),
                    "Booking received — " + service.getName(),
                    EmailTemplates.bookingCreated(
                            req.clientName(), number, service.getName(),
                            req.bookingDate().toString(), req.startTime().toString(),
                            booking.getEndTime().toString(), service.getPrice() != null
                                    ? "R " + service.getPrice().toPlainString() : "")
            );
        }

        return getBooking(tenantId, booking.getId());
    }

    /**
     * Reschedule an existing booking to a new date and time.
     *
     * WHY not cancel + recreate?
     * Cancelling and recreating:
     *   - Changes the booking number (confuses clients who have BK-2026-00043)
     *   - Loses the "created at" timestamp (reporting shows a gap)
     *   - Can't distinguish a reschedule from a cancel+new in analytics
     *
     * The reschedule endpoint keeps the same booking record, stores the
     * original date/time for audit, and updates to the new slot.
     */
    @Transactional
    public BookingResponse rescheduleBooking(TenantId tenantId, UUID id,
                                             RescheduleBookingRequest req) {
        Booking booking = findByTenant(tenantId, id);

        if (!List.of("PENDING", "CONFIRMED").contains(booking.getStatus())) {
            throw new ConflictException(
                    "Only PENDING or CONFIRMED bookings can be rescheduled. "
                            + "Current status: " + booking.getStatus());
        }

        BookingService service = serviceRepo.findActiveById(tenantId, booking.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BookingService", booking.getServiceId().toString()));

        LocalTime newEnd = req.newStartTime().plusMinutes(service.getDurationMinutes());

        // Conflict check for the new slot — exclude this booking itself
        if (booking.getStaffId() != null) {
            LocalTime effectiveEnd = newEnd.plusMinutes(service.getBufferAfterMinutes());
            List<Booking> conflicts = bookingRepo.findConflicts(
                    booking.getStaffId(), req.newDate(), req.newStartTime(), effectiveEnd);
            // Exclude the booking being rescheduled from conflict results
            conflicts = conflicts.stream()
                    .filter(c -> !c.getId().equals(id))
                    .toList();
            if (!conflicts.isEmpty()) {
                throw new ConflictException("The new time slot is not available.");
            }
        }

        booking.reschedule(req.newDate(), req.newStartTime(), newEnd);
        bookingRepo.save(booking);

        log.info("Rescheduled booking={} from {}/{} to {}/{}",
                booking.getBookingNumber(),
                booking.getOriginalBookingDate(), booking.getOriginalStartTime(),
                req.newDate(), req.newStartTime());

        return getBooking(tenantId, id);
    }

    @Transactional
    public BookingResponse confirmBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.confirm();
        bookingRepo.save(b);
        log.info("Confirmed booking={}", b.getBookingNumber());

        // Email client — their appointment is locked in; attach a PDF confirmation
        if (b.getClientEmail() != null && !b.getClientEmail().isBlank()) {
            String serviceName = serviceRepo.findActiveById(tenantId, b.getServiceId())
                    .map(BookingService::getName).orElse("appointment");
            String htmlBody = EmailTemplates.bookingConfirmed(
                    b.getClientName(), b.getBookingNumber(), serviceName,
                    b.getBookingDate().toString(), b.getStartTime().toString(),
                    b.getEndTime().toString());
            try {
                byte[] pdf = confirmationPdfService.generate(tenantId, b);
                emailService.sendWithAttachment(
                        b.getClientEmail(),
                        "Booking confirmed — " + serviceName,
                        htmlBody,
                        "booking-confirmation-" + b.getBookingNumber() + ".pdf",
                        pdf);
            } catch (Exception ex) {
                // PDF generation failure must not roll back the confirmation.
                // Fall back to sending email without attachment.
                log.warn("[Bookings] PDF generation failed for booking={} — sending without attachment: {}",
                        b.getBookingNumber(), ex.getMessage());
                emailService.send(b.getClientEmail(),
                        "Booking confirmed — " + serviceName, htmlBody);
            }
        }
        return getBooking(tenantId, id);
    }

    @Transactional
    public BookingResponse startBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.start();
        bookingRepo.save(b);
        return getBooking(tenantId, id);
    }

    @Transactional
    public BookingResponse completeBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.complete();
        bookingRepo.save(b);
        log.info("Completed booking={}", b.getBookingNumber());
        return getBooking(tenantId, id);
    }

    @Transactional
    public BookingResponse cancelBooking(TenantId tenantId, UUID id, String reason) {
        Booking b = findByTenant(tenantId, id);
        b.cancel(reason);
        bookingRepo.save(b);
        log.info("Cancelled booking={} reason={}", b.getBookingNumber(), reason);

        // Email client — inform them of the cancellation
        if (b.getClientEmail() != null && !b.getClientEmail().isBlank()) {
            String serviceName = serviceRepo.findActiveById(tenantId, b.getServiceId())
                    .map(BookingService::getName).orElse("appointment");
            emailService.send(
                    b.getClientEmail(),
                    "Booking cancelled — " + serviceName,
                    EmailTemplates.bookingCancelled(
                            b.getClientName(), b.getBookingNumber(), serviceName,
                            b.getBookingDate().toString(), reason)
            );
        }
        return getBooking(tenantId, id);
    }

    // ── Skill mapping (staff–service assignments) ────────────────────────────────

    /**
     * Assign a list of staff members to a service (replaces existing assignments).
     * WHY replace rather than add? It avoids stale assignments — if a staff member
     * leaves and is removed from the list, the old assignment is gone.
     * The UI sends the full current selection every time.
     */
    @Transactional
    public void setServiceStaff(TenantId tenantId, UUID serviceId, java.util.List<UUID> staffIds) {
        // Guard: service must belong to this tenant
        serviceRepo.findActiveById(tenantId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingService", serviceId.toString()));
        // Replace all existing assignments
        serviceStaffRepo.deleteByServiceId(serviceId);
        staffIds.forEach(staffId ->
                serviceStaffRepo.save(BookingServiceStaff.of(serviceId, staffId)));
        log.info("[Bookings] Set service={} staff assignments={} tenant={}", serviceId, staffIds.size(), tenantId);
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> getServiceStaff(TenantId tenantId, UUID serviceId) {
        serviceRepo.findActiveById(tenantId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingService", serviceId.toString()));
        return serviceStaffRepo.findStaffIdsByService(serviceId);
    }

    // ── Staff management ──────────────────────────────────────────────────────

    @Transactional
    public StaffResponse updateStaff(TenantId tenantId, UUID id, UpdateStaffRequest req) {
        BookingStaff s = staffRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingStaff", id.toString()));
        s.update(req.name(), req.email(), req.phone());
        staffRepo.save(s);
        log.info("Updated staff={} tenant={}", id, tenantId);
        return toStaffResponse(s);
    }

    @Transactional
    public void deactivateStaff(TenantId tenantId, UUID id) {
        BookingStaff s = staffRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingStaff", id.toString()));
        s.deactivate();
        staffRepo.save(s);
        log.info("Deactivated staff={} tenant={}", id, tenantId);
    }

    // ── Service management ────────────────────────────────────────────────────

    @Transactional
    public void deleteService(TenantId tenantId, UUID id) {
        // WHY soft delete and not hard delete?
        // Bookings reference service_id.  A hard DELETE would violate the
        // FK constraint on the bookings table and destroy historical records
        // (past revenue, analytics).  Soft delete marks the service as inactive
        // and deleted_at is set — it disappears from the active services list
        // but past bookings still resolve correctly.
        BookingService s = serviceRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingService", id.toString()));
        s.softDelete();
        serviceRepo.save(s);
        log.info("Soft-deleted service={} name='{}' tenant={}", id, s.getName(), tenantId);
    }

    @Transactional
    public BookingResponse markNoShow(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.markNoShow();
        bookingRepo.save(b);
        return getBooking(tenantId, id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the raw Booking entity — used by the PDF controller. */
    @Transactional(readOnly = true)
    public Booking getBookingEntity(TenantId tenantId, UUID id) {
        return findByTenant(tenantId, id);
    }

    private Booking findByTenant(TenantId tenantId, UUID id) {
        return bookingRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id.toString()));
    }

    /**
     * Maps a JDBC ResultSet row (with joined service_name + staff_name) to BookingResponse.
     * service_name and staff_name come from the JOIN — no extra queries needed.
     */
    private BookingResponse mapBookingResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        String staffIdStr = rs.getString("staff_id");
        String invIdStr   = rs.getString("invoice_id");

        var confirmedTs   = rs.getTimestamp("confirmed_at");
        var completedTs   = rs.getTimestamp("completed_at");
        var createdTs     = rs.getTimestamp("created_at");
        var rescheduledTs = rs.getTimestamp("rescheduled_at");

        return new BookingResponse(
                UUID.fromString(rs.getString("id")),
                rs.getString("booking_number"),
                UUID.fromString(rs.getString("service_id")),
                rs.getString("service_name"),
                staffIdStr != null ? UUID.fromString(staffIdStr) : null,
                rs.getString("staff_name"),
                rs.getString("client_name"),
                rs.getString("client_email"),
                rs.getString("client_phone"),
                rs.getObject("booking_date", LocalDate.class),
                rs.getObject("start_time",   LocalTime.class),
                rs.getObject("end_time",     LocalTime.class),
                rs.getInt("duration_minutes"),
                rs.getString("status"),
                rs.getBigDecimal("price"),
                rs.getString("notes"),
                invIdStr != null ? UUID.fromString(invIdStr) : null,
                confirmedTs   != null ? confirmedTs.toInstant()   : null,
                completedTs   != null ? completedTs.toInstant()   : null,
                createdTs     != null ? createdTs.toInstant()     : null,
                rs.getString("cancellation_reason"),
                rs.getObject("original_booking_date", LocalDate.class),
                rescheduledTs != null ? rescheduledTs.toInstant() : null
        );
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ServiceResponse toServiceResponse(BookingService s) {
        return new ServiceResponse(
                s.getId(), s.getName(), s.getDescription(),
                s.getDurationMinutes(), s.getPrice(), s.getCurrency(),
                s.getColor(), s.isActive(),
                s.getBufferBeforeMinutes(), s.getBufferAfterMinutes(),
                s.getMinLeadTimeMinutes(), s.getMaxAdvanceDays(),
                s.getCreatedAt());
    }

    private StaffResponse toStaffResponse(BookingStaff s) {
        return new StaffResponse(s.getId(), s.getName(), s.getEmail(),
                s.getPhone(), s.getEmployeeId(), s.isActive());
    }
}
