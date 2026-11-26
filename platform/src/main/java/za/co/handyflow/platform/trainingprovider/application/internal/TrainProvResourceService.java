package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.ResourceRules;
import za.co.handyflow.platform.trainingprovider.dto.ResourceDtos.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The site resource register. Records which equipment and similar resources a delivery site holds and the state each is in. Every change
 * of state or site is kept in the resource's history with a reason. Nothing is deleted, and listing a resource says nothing about standards.
 */
@Service
@RequiredArgsConstructor
public class TrainProvResourceService {

    private final TrainProvSiteResourceRepository resources;
    private final TrainProvSiteResourceEventRepository events;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvVenueRepository venues;
    private final TrainProvNumberGenerator numbers;

    public List<ResourceView> list(TenantId t, UUID siteId, String status, String type) {
        return resources.search(t, siteId, blank(status), blank(type)).stream().<ResourceView>map(r -> view(t, r)).toList();
    }

    public ResourceDetail get(TenantId t, UUID id) {
        TrainProvSiteResource r = resource(t, id);
        return new ResourceDetail(view(t, r), events.forResource(t, id).stream().<EventView>map(e -> eventView(t, e)).toList());
    }

    @Transactional
    public ResourceView create(TenantId t, ResourceRequest r, UUID by, LocalDate today) {
        TrainProvDeliverySite site = site(t, r.siteId());
        if (!"ACTIVE".equals(site.getStatus())) {
            throw new HandyFlowException("That site is not active", HttpStatus.CONFLICT, "TP_RESOURCE_STATE");
        }
        checkVenue(t, r.venueId(), site.getId());
        checkIdentifier(t, r.identifier(), null);
        int qty = r.quantity() == null ? 1 : r.quantity();
        TrainProvSiteResource res = guard(() -> TrainProvSiteResource.create(t, numbers.nextResourceNumber(t), site.getId(), r.venueId(), r.name(), r.resourceType(), r.identifier(),
                qty, r.shareable(), r.notes(), by));
        resources.save(res);
        events.save(TrainProvSiteResourceEvent.of(t, res.getId(), "ADDED", null, res.getStatus(), null, site.getId(), null, today, by));
        return view(t, res);
    }

    /** Changes the description. The site, the state and the resource number are changed only by a move or a state change. */
    @Transactional
    public ResourceView update(TenantId t, UUID id, ResourceRequest r, UUID by, LocalDate today) {
        TrainProvSiteResource res = resource(t, id);
        checkVenue(t, r.venueId(), res.getSiteId());
        checkIdentifier(t, r.identifier(), res.getId());
        int qty = r.quantity() == null ? 1 : r.quantity();
        guard(() -> { res.change(r.venueId(), r.name(), r.resourceType(), r.identifier(), qty, r.shareable(), r.notes()); return null; });
        resources.save(res);
        events.save(TrainProvSiteResourceEvent.of(t, res.getId(), "CHANGED", null, null, null, null, null, today, by));
        return view(t, res);
    }

    @Transactional
    public ResourceView changeStatus(TenantId t, UUID id, StatusRequest r, UUID by, LocalDate today) {
        TrainProvSiteResource res = resource(t, id);
        String from = res.getStatus();
        guard(() -> { res.setStatus(r.toStatus(), r.reason(), r.changedOn(), today); return null; });
        resources.save(res);
        events.save(TrainProvSiteResourceEvent.of(t, res.getId(), "STATUS", from, r.toStatus(), null, null, r.reason(), r.changedOn(), by));
        return view(t, res);
    }

    @Transactional
    public ResourceView move(TenantId t, UUID id, MoveRequest r, UUID by, LocalDate today) {
        TrainProvSiteResource res = resource(t, id);
        TrainProvDeliverySite target = site(t, r.siteId());
        boolean venueOk = r.venueId() == null || venues.findForTenant(t, r.venueId()).filter(v -> v.getSiteId().equals(target.getId())).isPresent();
        String problem = ResourceRules.moveProblem(res.getStatus(), res.getSiteId().equals(target.getId()), "ACTIVE".equals(target.getStatus()), venueOk, r.reason());
        if (problem == null && r.movedOn().isAfter(today)) {
            problem = "The move cannot be dated in the future";
        }
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_RESOURCE_STATE");
        }
        UUID from = res.getSiteId();
        res.moveTo(target.getId(), r.venueId());
        resources.save(res);
        events.save(TrainProvSiteResourceEvent.of(t, res.getId(), "MOVED", null, null, from, target.getId(), r.reason(), r.movedOn(), by));
        return view(t, res);
    }

    // ---- helpers ----

    private void checkVenue(TenantId t, UUID venueId, UUID siteId) {
        if (venueId != null && venues.findForTenant(t, venueId).filter(v -> v.getSiteId().equals(siteId)).isEmpty()) {
            throw new HandyFlowException("That venue is not at the site", HttpStatus.BAD_REQUEST, "TP_RESOURCE_INVALID");
        }
    }

    private void checkIdentifier(TenantId t, String identifier, UUID excludeId) {
        if (identifier != null && !identifier.isBlank() && resources.identifierTaken(t, identifier.trim(), excludeId)) {
            throw new HandyFlowException("Another resource that is not retired already has that identifier", HttpStatus.CONFLICT, "TP_RESOURCE_STATE");
        }
    }

    private ResourceView view(TenantId t, TrainProvSiteResource r) {
        return new ResourceView(r.getId(), r.getResourceNo(), r.getSiteId(), siteName(t, r.getSiteId()), r.getVenueId(),
                r.getVenueId() == null ? null : venues.findForTenant(t, r.getVenueId()).map(TrainProvVenue::getName).orElse(null), r.getName(), r.getResourceType(), r.getIdentifier(),
                r.getQuantity(), r.isShareable(), r.getNotes(), r.getStatus(), r.getStatusReason(), r.getStatusChangedOn(),
                ResourceRules.nextStatuses(r.getStatus()).stream().sorted(Comparator.naturalOrder()).toList());
    }

    private EventView eventView(TenantId t, TrainProvSiteResourceEvent e) {
        return new EventView(e.getId(), e.getEventType(), e.getFromStatus(), e.getToStatus(), e.getFromSiteId() == null ? null : siteName(t, e.getFromSiteId()),
                e.getToSiteId() == null ? null : siteName(t, e.getToSiteId()), e.getNote(), e.getOccurredOn(), e.getCreatedAt());
    }

    private String siteName(TenantId t, UUID id) {
        return sites.findForTenant(t, id).map(TrainProvDeliverySite::getName).orElse(null);
    }

    private TrainProvSiteResource resource(TenantId t, UUID id) {
        return resources.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvSiteResource", id.toString()));
    }

    private TrainProvDeliverySite site(TenantId t, UUID id) {
        return sites.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_RESOURCE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_RESOURCE_INVALID");
        }
    }
}
