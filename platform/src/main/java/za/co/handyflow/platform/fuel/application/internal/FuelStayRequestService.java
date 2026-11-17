package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStayRequest;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRequestRepository;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.fuel.dto.StayRequestDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * Driver self-service at the truck stop, through the customer portal. A portal user with an ACTIVE grant on a Fuel customer account may see free bays,
 * see the stays charged to that account, and <b>ask</b> for an overnight stay. It is a request, not a booking:
 * <ul>
 *   <li>Staff approve it, and approval books the stay through the ordinary stay code, so the bay lock, the capacity rule, the credit-hold rule and the
 *       charge to the account are exactly as when staff book. If that refuses, the request stays pending and the refusal reaches the staff member.</li>
 *   <li>Staff may decline with a reason the customer sees. The customer may cancel while it is pending.</li>
 *   <li>Every portal call goes through the grant first, so one customer can never reach another's account. The Truck Stop division must be active.</li>
 *   <li>A customer cannot pile up requests (ten open) or send the same one twice. Free bays are counts only, never other customers' trucks.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelStayRequestService {

    static final int MAX_OPEN_REQUESTS = 10;
    static final int MAX_AVAILABILITY_DAYS = 14;
    static final String PORTAL_NOTE = "Requested through the customer portal";
    private static final java.time.ZoneId SA = java.time.ZoneId.of("Africa/Johannesburg");

    private final FuelPortalDataService portal;
    private final FuelTruckStopStayRequestRepository requests;
    private final FuelTruckStopStayRepository stays;
    private final FuelTruckStopStayService stayService;
    private final FuelDivisionGuard divisions;
    private final FuelCustomerAccountRepository accounts;
    private final CrmFacade crm;
    private final PortalUserRepository portalUsers;
    private final NotificationService notifications;
    private final TenantAdminRecipients adminRecipients;

    static LocalDate today() { return LocalDate.now(SA); }

    // ── customer side ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortalAvailability availability(UUID portalUserId, UUID accountId, LocalDate from, int days) {
        TenantId tenant = tenantOf(portalUserId, accountId);
        LocalDate start = from == null || from.isBefore(today()) ? today() : from;
        var settings = stayService.settings(tenant);
        CapacityView cap = stayService.capacity(tenant, start, Math.max(1, Math.min(days, MAX_AVAILABILITY_DAYS)), today());
        List<PortalNight> nights = cap.nights().stream().map(n -> new PortalNight(n.date(), n.free())).toList();
        return new PortalAvailability(settings.nightlyRate(), settings.nightlyRate() != null, nights);
    }

    @Transactional
    public PortalStayRequestView request(UUID portalUserId, UUID accountId, StayRequestBody req) {
        TenantId tenant = tenantOf(portalUserId, accountId);
        LocalDate today = today();
        if (stayService.settings(tenant).nightlyRate() == null) {
            throw new HandyFlowException("Overnight parking is not open for requests yet. Please call us.", HttpStatus.CONFLICT, "NOT_OPEN");
        }
        accounts.findActiveById(tenant, accountId).orElseThrow(FuelStayRequestService::notFound);
        String email = portalUsers.findById(portalUserId).map(u -> u.getEmail()).orElse("a portal user");
        FuelTruckStopStayRequest r = FuelTruckStopStayRequest.create(tenant, accountId, portalUserId, email, req.vehicleRegistration(), req.driverName(), req.driverPhone(),
                req.arrivalDate(), req.departureDate(), req.note(), today);
        if (requests.countSamePending(tenant, accountId, r.getVehicleRegistration(), r.getArrivalDate(), r.getDepartureDate()) > 0) {
            throw new HandyFlowException("You already asked for this truck on these dates. It is waiting for us to confirm.", HttpStatus.CONFLICT, "DUPLICATE_REQUEST");
        }
        long open = requests.countPending(tenant, accountId);
        if (open >= MAX_OPEN_REQUESTS) {
            throw new HandyFlowException("You already have " + open + " requests waiting for us. Please wait for them to be confirmed, or call us.", HttpStatus.CONFLICT, "TOO_MANY_REQUESTS");
        }
        // Early, friendly feedback. The booking on approval checks again and is the authority.
        CapacityView cap = stayService.capacity(tenant, r.getArrivalDate(), r.nights(), today);
        for (NightView n : cap.nights()) {
            if (n.free() != null && n.free() <= 0 && n.date().isBefore(r.getDepartureDate())) {
                throw new HandyFlowException("We are full on the night of " + n.date() + ". Please choose other dates, or call us.", HttpStatus.CONFLICT, "NO_BAY");
            }
        }
        requests.save(r);
        notifyStaff(tenant, r);
        log.info("Truck stop stay requested id={} account={} tenant={}", r.getId(), accountId, tenant);
        return portalView(r);
    }

    @Transactional(readOnly = true)
    public List<PortalStayRequestView> myRequests(UUID portalUserId, UUID accountId) {
        TenantId tenant = tenantOf(portalUserId, accountId);
        return requests.findByAccount(tenant, accountId, PageRequest.of(0, 50)).stream().map(FuelStayRequestService::portalView).toList();
    }

    @Transactional
    public PortalStayRequestView cancel(UUID portalUserId, UUID accountId, UUID requestId) {
        TenantId tenant = tenantOf(portalUserId, accountId);
        FuelTruckStopStayRequest r = requests.findForUpdate(tenant, requestId).filter(x -> x.getAccountId().equals(accountId)).orElseThrow(FuelStayRequestService::notFound);
        r.cancel(portalUserId);
        return portalView(requests.save(r));
    }

    @Transactional(readOnly = true)
    public List<PortalStayView> myStays(UUID portalUserId, UUID accountId) {
        TenantId tenant = tenantOf(portalUserId, accountId);
        return stays.findForAccount(tenant, accountId, PageRequest.of(0, 50)).stream()
                .map(s -> new PortalStayView(s.getId(), s.getVehicleRegistration(), s.getDriverName(), s.getArrivalDate(), s.getDepartureDate(), s.nights(),
                        s.getStatus(), s.getRatePerNight(), s.amount(), s.getInvoiceNumber()))
                .toList();
    }

    // ── staff side ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaffStayRequestView> list(TenantId tenantId, String status, int limit) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !Set.of("PENDING", "APPROVED", "DECLINED", "CANCELLED").contains(s)) throw new IllegalArgumentException("Status must be PENDING, APPROVED, DECLINED or CANCELLED.");
        List<FuelTruckStopStayRequest> rows = requests.findByStatus(tenantId, s, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
        Map<UUID, String> names = new HashMap<>();
        for (FuelTruckStopStayRequest r : rows) names.computeIfAbsent(r.getAccountId(), id -> customerName(tenantId, id));
        return rows.stream().map(r -> staffView(r, names.get(r.getAccountId()))).toList();
    }

    /**
     * Books the stay through the normal code, charged to the customer's account, and marks the request approved. If the booking is refused (no bay, credit hold,
     * arrival now in the past) nothing changes and the request stays pending, so staff can decline it with a reason instead.
     */
    @Transactional
    public StaffStayRequestView approve(TenantId tenantId, UUID id, String note, UUID by) {
        divisions.require(tenantId, Set.of(FuelDivision.TRUCK_STOP));
        FuelTruckStopStayRequest r = requests.findForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Stay request", id.toString()));
        if (!r.isPending()) throw new IllegalStateException("This request has already been " + r.getStatus().toLowerCase() + ".");
        String stayNote = PORTAL_NOTE + (r.getNote() == null ? "" : ": " + r.getNote());
        if (stayNote.length() > 300) stayNote = stayNote.substring(0, 300);
        StayView stay = stayService.book(tenantId, new BookRequest(r.getVehicleRegistration(), r.getDriverName(), r.getDriverPhone(), r.getArrivalDate(),
                r.getDepartureDate(), stayNote, r.getAccountId()), by, today());
        r.approve(by, stay.id(), note);
        requests.save(r);
        log.info("Truck stop stay request approved id={} stay={} by={}", id, stay.id(), by);
        return staffView(r, customerName(tenantId, r.getAccountId()));
    }

    @Transactional
    public StaffStayRequestView decline(TenantId tenantId, UUID id, String reason, UUID by) {
        divisions.require(tenantId, Set.of(FuelDivision.TRUCK_STOP));
        FuelTruckStopStayRequest r = requests.findForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Stay request", id.toString()));
        r.decline(by, reason);
        requests.save(r);
        return staffView(r, customerName(tenantId, r.getAccountId()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The grant decides the tenant and the account. Same answer for "no such account" and "not yours". The Truck Stop division must be active. */
    private TenantId tenantOf(UUID portalUserId, UUID accountId) {
        FuelPortalAccessGrant g = portal.requireGrant(portalUserId, accountId);
        TenantId tenant = TenantId.of(g.getTenantId());
        try {
            divisions.require(tenant, Set.of(FuelDivision.TRUCK_STOP));
        } catch (HandyFlowException e) {
            throw new HandyFlowException("Truck stop services are not available here.", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return tenant;
    }

    private static HandyFlowException notFound() { return new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    private String customerName(TenantId tenant, UUID accountId) {
        return accounts.findActiveById(tenant, accountId).flatMap(a -> crm.findCustomerById(tenant, a.getCrmCustomerId())).map(CustomerSummary::name).orElse("Customer");
    }

    static PortalStayRequestView portalView(FuelTruckStopStayRequest r) {
        return new PortalStayRequestView(r.getId(), r.getVehicleRegistration(), r.getDriverName(), r.getDriverPhone(), r.getArrivalDate(), r.getDepartureDate(), r.nights(),
                r.getStatus(), r.getNote(), r.getDecisionNote(), r.getCreatedAt());
    }

    static StaffStayRequestView staffView(FuelTruckStopStayRequest r, String customerName) {
        return new StaffStayRequestView(r.getId(), r.getAccountId(), customerName, r.getRequestedByEmail(), r.getVehicleRegistration(), r.getDriverName(), r.getDriverPhone(),
                r.getArrivalDate(), r.getDepartureDate(), r.nights(), r.getStatus(), r.getNote(), r.getDecisionNote(), r.getStayId(), r.getCreatedAt(), r.getDecidedAt());
    }

    /** Never fails the request: it is already saved. */
    private void notifyStaff(TenantId tenant, FuelTruckStopStayRequest r) {
        try {
            List<Recipient> recipients = adminRecipients.resolveTenantAdmins(tenant);
            if (recipients.isEmpty()) return;
            notifications.send(NotificationRequest.builder()
                    .tenantId(tenant)
                    .type(NotificationType.FUEL_PORTAL_REQUEST)
                    .title("Overnight stay request from a customer")
                    .message(r.getRequestedByEmail() + " asked for " + r.getVehicleRegistration() + " from " + r.getArrivalDate() + " to " + r.getDepartureDate()
                            + " (" + r.nights() + " night" + (r.nights() == 1 ? "" : "s") + "). It is waiting to be confirmed.")
                    .actionUrl("/fuel/truck-stop")
                    .sourceModule("fuel")
                    .sourceEntityId(String.valueOf(r.getId()))
                    .recipients(recipients)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not notify staff of stay request {}: {}", r.getId(), ex.getMessage());
        }
    }
}
