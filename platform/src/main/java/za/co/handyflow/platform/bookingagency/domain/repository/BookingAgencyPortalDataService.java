package za.co.handyflow.platform.bookingagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookingagency.domain.model.*;
import za.co.handyflow.platform.bookingagency.domain.repository.*;
import za.co.handyflow.platform.bookingagency.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Third mirror of the same confirmed-working portal-data pattern.
 * Scoped to bookings/resources/offerings visibility only — DELIBERATELY
 * no invoice/billing tab. This module's billing model is still an open
 * question (see package-info.java) — showing a billing tab backed by
 * nothing real would be worse than not having one yet. Add it once the
 * billing layer actually exists, following the exact same pattern
 * payrollbureau and recruitmentagency's portals already use for theirs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAgencyPortalDataService {

    private final BookPortalAccessGrantRepository grantRepo;
    private final BookAgencyClientRepository clientRepo;
    private final BookAgencyResourceRepository resourceRepo;
    private final BookAgencyOfferingRepository offeringRepo;
    private final BookAgencyBookingRepository bookingRepo;

    @Transactional(readOnly = true)
    public List<PortalClientSummaryResponse> getMyClients(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> clientRepo.findById(g.getClientId())
                        .map(c -> new PortalClientSummaryResponse(c.getId(), c.getTradingName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getMyResources(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return resourceRepo.findActiveByClient(clientId).stream().map(this::toResourceResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferingResponse> getMyOfferings(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return offeringRepo.findActiveByClient(clientId).stream().map(this::toOfferingResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(UUID portalUserId, UUID clientId, Pageable pageable) {
        requireAccess(portalUserId, clientId);
        return bookingRepo.findByClient(clientId, pageable).map(b -> {
            String resourceName = resourceRepo.findById(b.getResourceId()).map(BookAgencyResource::getName).orElse("Unknown");
            String offeringName = offeringRepo.findById(b.getOfferingId()).map(BookAgencyOffering::getName).orElse("Unknown");
            return toBookingResponse(b, resourceName, offeringName);
        });
    }

    private BookPortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    private ResourceResponse toResourceResponse(BookAgencyResource r) {
        return new ResourceResponse(r.getId(), r.getClientId(), r.getName(), r.getRoleDescription(),
                r.getWorkingHoursStart(), r.getWorkingHoursEnd(), r.isActive());
    }

    private OfferingResponse toOfferingResponse(BookAgencyOffering o) {
        return new OfferingResponse(o.getId(), o.getClientId(), o.getName(), o.getDurationMinutes(),
                o.getBufferMinutes(), o.getPrice(), o.isActive());
    }

    private BookingResponse toBookingResponse(BookAgencyBooking b, String resourceName, String offeringName) {
        return new BookingResponse(b.getId(), b.getBookingNumber(), b.getClientId(),
                b.getResourceId(), resourceName, b.getOfferingId(), offeringName,
                b.getCustomerName(), b.getCustomerPhone(), b.getCustomerEmail(),
                b.getStartDatetime(), b.getEndDatetime(), b.getStatus(), b.getNotes(), b.getCreatedAt());
    }
}