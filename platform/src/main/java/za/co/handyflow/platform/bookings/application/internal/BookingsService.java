package za.co.handyflow.platform.bookings.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.domain.model.*;
import za.co.handyflow.platform.bookings.domain.repository.*;
import za.co.handyflow.platform.bookings.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingsService {

    private final BookingRepository         bookingRepo;
    private final BookingServiceRepository  serviceRepo;
    private final BookingStaffRepository    staffRepo;
    private final BookingAvailabilityRepository availabilityRepo;
    private final BookingBlockRepository    blockRepo;
    private final BookingNumberGenerator    numberGen;
    private final SlotEngine                slotEngine;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ── Services ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ServiceResponse> getServices(TenantId tenantId) {
        return serviceRepo.findAllActive(tenantId)
                .stream().map(this::toServiceResponse).toList();
    }

    @Transactional
    public ServiceResponse createService(TenantId tenantId, CreateServiceRequest req) {
        BookingService s = BookingService.create(tenantId, req.name(),
                req.description(), req.durationMinutes(), req.price(), req.color());
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
                req.price(), req.color());
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
        // Upsert — delete existing for this staff + day then insert
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
        return slotEngine.getAvailableSlots(tenantId, staffId, date,
                service.getDurationMinutes());
    }

    // ── Bookings ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(TenantId tenantId, String status,
                                             LocalDate date, UUID staffId,
                                             Pageable pageable) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM bookings b WHERE b.tenant_id = ?");
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM bookings b WHERE b.tenant_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(tenantId.getValue());

        if (status != null && !status.isBlank()) {
            sql.append(" AND b.status = ?");
            countSql.append(" AND b.status = ?");
            params.add(status);
        }
        if (date != null) {
            sql.append(" AND b.booking_date = ?");
            countSql.append(" AND b.booking_date = ?");
            params.add(date);
        }
        if (staffId != null) {
            sql.append(" AND b.staff_id = ?");
            countSql.append(" AND b.staff_id = ?");
            params.add(staffId);
        }

        sql.append(" ORDER BY b.booking_date DESC, b.start_time ASC LIMIT ? OFFSET ?");

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageable.getPageSize());
        pageParams.add(pageable.getOffset());

        // Map directly to BookingResponse — avoids needing Booking setters
        List<BookingResponse> responses = jdbc.query(
                sql.toString(),
                (rs, rowNum) -> {
                    UUID bId        = UUID.fromString(rs.getString("id"));
                    UUID serviceId  = UUID.fromString(rs.getString("service_id"));
                    String svcIdStr = rs.getString("staff_id");
                    UUID bStaffId   = svcIdStr != null ? UUID.fromString(svcIdStr) : null;
                    String invIdStr = rs.getString("invoice_id");
                    UUID invoiceId  = invIdStr != null ? UUID.fromString(invIdStr) : null;

                    // Resolve names via existing repos
                    String serviceName = serviceRepo.findActiveById(tenantId, serviceId)
                            .map(BookingService::getName).orElse("—");
                    String staffName = bStaffId != null
                            ? staffRepo.findByTenantAndId(tenantId, bStaffId)
                            .map(BookingStaff::getName).orElse(null)
                            : null;

                    java.sql.Timestamp confirmedTs  = rs.getTimestamp("confirmed_at");
                    java.sql.Timestamp completedTs  = rs.getTimestamp("completed_at");
                    java.sql.Timestamp createdTs    = rs.getTimestamp("created_at");

                    return new BookingResponse(
                            bId,
                            rs.getString("booking_number"),
                            serviceId,
                            serviceName,
                            bStaffId,
                            staffName,
                            rs.getString("client_name"),
                            rs.getString("client_email"),
                            rs.getString("client_phone"),
                            rs.getObject("booking_date", java.time.LocalDate.class),
                            rs.getObject("start_time",   java.time.LocalTime.class),
                            rs.getObject("end_time",     java.time.LocalTime.class),
                            rs.getInt("duration_minutes"),
                            rs.getString("status"),
                            rs.getBigDecimal("price"),
                            rs.getString("notes"),
                            invoiceId,
                            confirmedTs  != null ? confirmedTs.toInstant()  : null,
                            completedTs  != null ? completedTs.toInstant()  : null,
                            createdTs    != null ? createdTs.toInstant()    : null
                    );
                },
                pageParams.toArray()
        );

        Long total = jdbc.queryForObject(
                countSql.toString(), Long.class, params.toArray());

        return new PageImpl<>(responses, pageable, total != null ? total : 0L);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(TenantId tenantId, UUID id) {
        return bookingRepo.findByTenantAndId(tenantId, id)
                .map(b -> toBookingResponse(b, tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id.toString()));
    }

    @Transactional
    public BookingResponse createBooking(TenantId tenantId, CreateBookingRequest req) {
        BookingService service = serviceRepo.findActiveById(tenantId, req.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("BookingService",
                        req.serviceId().toString()));

        // Conflict check
        if (req.staffId() != null) {
            var slotEnd = req.startTime().plusMinutes(service.getDurationMinutes());
            var conflicts = bookingRepo.findConflicts(req.staffId(),
                    req.bookingDate(), req.startTime(), slotEnd);
            if (!conflicts.isEmpty())
                throw new IllegalStateException(
                        "Time slot not available — " + conflicts.size() + " conflict(s) found");
        }

        String number = numberGen.next(tenantId);
        Booking booking = Booking.create(tenantId, number, req.serviceId(),
                req.staffId(), req.customerId(), req.clientName(), req.clientEmail(),
                req.clientPhone(), req.bookingDate(), req.startTime(),
                service.getDurationMinutes(), service.getPrice(), req.notes());
        bookingRepo.save(booking);

        log.info("Created booking={} client={} date={} service={}",
                number, req.clientName(), req.bookingDate(), service.getName());
        return toBookingResponse(booking, tenantId);
    }

    @Transactional
    public BookingResponse confirmBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.confirm();
        bookingRepo.save(b);
        log.info("Confirmed booking={}", b.getBookingNumber());
        return toBookingResponse(b, tenantId);
    }

    @Transactional
    public BookingResponse startBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.start();
        bookingRepo.save(b);
        return toBookingResponse(b, tenantId);
    }

    @Transactional
    public BookingResponse completeBooking(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.complete();
        bookingRepo.save(b);
        log.info("Completed booking={}", b.getBookingNumber());
        return toBookingResponse(b, tenantId);
    }

    @Transactional
    public BookingResponse cancelBooking(TenantId tenantId, UUID id, String reason) {
        Booking b = findByTenant(tenantId, id);
        b.cancel(reason);
        bookingRepo.save(b);
        log.info("Cancelled booking={} reason={}", b.getBookingNumber(), reason);
        return toBookingResponse(b, tenantId);
    }

    @Transactional
    public BookingResponse markNoShow(TenantId tenantId, UUID id) {
        Booking b = findByTenant(tenantId, id);
        b.markNoShow();
        bookingRepo.save(b);
        return toBookingResponse(b, tenantId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Booking findByTenant(TenantId tenantId, UUID id) {
        return bookingRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id.toString()));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private BookingResponse toBookingResponse(Booking b, TenantId tenantId) {
        String serviceName = serviceRepo.findActiveById(tenantId, b.getServiceId())
                .map(BookingService::getName).orElse("—");
        String staffName = b.getStaffId() != null
                ? staffRepo.findByTenantAndId(tenantId, b.getStaffId())
                .map(BookingStaff::getName).orElse("—")
                : null;
        return new BookingResponse(b.getId(), b.getBookingNumber(),
                b.getServiceId(), serviceName, b.getStaffId(), staffName,
                b.getClientName(), b.getClientEmail(), b.getClientPhone(),
                b.getBookingDate(), b.getStartTime(), b.getEndTime(),
                b.getDurationMinutes(), b.getStatus(), b.getPrice(),
                b.getNotes(), b.getInvoiceId(), b.getConfirmedAt(),
                b.getCompletedAt(), b.getCreatedAt());
    }

    private ServiceResponse toServiceResponse(BookingService s) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(),
                s.getDurationMinutes(), s.getPrice(), s.getCurrency(),
                s.getColor(), s.isActive(), s.getCreatedAt());
    }

    private StaffResponse toStaffResponse(BookingStaff s) {
        return new StaffResponse(s.getId(), s.getName(), s.getEmail(),
                s.getPhone(), s.getEmployeeId(), s.isActive());
    }
}