package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.RouteDtos.RouteView;
import za.co.handyflow.platform.fuel.dto.RouteDtos.StopView;
import za.co.handyflow.platform.fuel.dto.RouteDtos.SuggestedOrder;
import za.co.handyflow.platform.fuel.dto.RouteDtos.SuggestedStop;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

/** Phase 8b: builds a suggested stop order for one route. Read-only: the planner applies it with the normal stop-order call. */
@Service
@RequiredArgsConstructor
public class FuelRouteOptimiserService {

    static final String NOTE_STRAIGHT = "Straight-line distances, so shorter than by road and blind to traffic. Stops with a delivery window keep their window order. Stops already on the road or delivered stay where they are.";
    static final String NOTE_FALLBACK = "Some sites have no map position, so stops were ordered by delivery window and then nearest first by the stored site distance. Add latitude and longitude to those sites for a straight-line suggestion.";

    private final FuelRouteService routeService;
    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerSiteRepository sites;

    @Transactional(readOnly = true)
    public SuggestedOrder suggest(TenantId tenantId, UUID routeId, BigDecimal startLat, BigDecimal startLng) {
        if ((startLat == null) != (startLng == null)) throw new IllegalArgumentException("Give both the start latitude and longitude, or neither.");
        if (startLat != null && startLat.abs().compareTo(new BigDecimal("90")) > 0) throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        if (startLng != null && startLng.abs().compareTo(new BigDecimal("180")) > 0) throw new IllegalArgumentException("Longitude must be between -180 and 180.");

        RouteView route = routeService.get(tenantId, routeId);
        if ("CANCELLED".equals(route.status())) throw new IllegalStateException("A cancelled route has no stops to order.");
        List<StopView> stops = new ArrayList<>(route.stops());
        stops.sort(Comparator.comparingInt(StopView::sequence));

        Map<UUID, UUID> siteOf = new HashMap<>();
        for (FuelDelivery d : deliveries.findByRoute(tenantId, routeId)) if (d.getSiteId() != null) siteOf.put(d.getId(), d.getSiteId());
        Map<UUID, FuelCustomerSite> siteById = new HashMap<>();
        if (!siteOf.isEmpty()) for (FuelCustomerSite s : sites.findAllById(new HashSet<>(siteOf.values()))) if (tenantId.equals(s.getTenantId())) siteById.put(s.getId(), s);

        List<FuelRouteOptimiser.Stop> input = new ArrayList<>();
        Map<UUID, Boolean> hasPoint = new HashMap<>();
        List<String> noPoint = new ArrayList<>();
        for (StopView v : stops) {
            FuelCustomerSite site = siteById.get(siteOf.get(v.deliveryId()));
            boolean movable = "SCHEDULED".equals(v.status());
            BigDecimal lat = site == null ? null : site.getLatitude(), lng = site == null ? null : site.getLongitude();
            BigDecimal dist = site == null ? null : site.getDistanceKm();
            boolean point = lat != null && lng != null;
            hasPoint.put(v.deliveryId(), point);
            if (movable && !point) noPoint.add(v.customerName() == null ? "(no customer)" : v.customerName());
            input.add(new FuelRouteOptimiser.Stop(v.deliveryId(), movable, lat, lng, dist, v.windowStart(), v.windowEnd()));
        }

        FuelRouteOptimiser.Result r = FuelRouteOptimiser.optimise(input, startLat, startLng);
        Map<UUID, StopView> byId = new HashMap<>();
        stops.forEach(v -> byId.put(v.deliveryId(), v));
        List<SuggestedStop> out = new ArrayList<>();
        for (int i = 0; i < r.order().size(); i++) {
            StopView v = byId.get(r.order().get(i));
            boolean movable = "SCHEDULED".equals(v.status());
            int previous = stops.indexOf(v) + 1;
            out.add(new SuggestedStop(v.deliveryId(), i + 1, previous, v.customerName(), v.status(), movable, previous != i + 1,
                    v.windowStart(), v.windowEnd(), hasPoint.getOrDefault(v.deliveryId(), false)));
        }
        BigDecimal saving = r.currentKm() != null && r.suggestedKm() != null ? r.currentKm().subtract(r.suggestedKm()).max(BigDecimal.ZERO) : null;
        return new SuggestedOrder(routeId, r.method(), r.changed(), r.currentKm(), r.suggestedKm(), saving, out,
                List.copyOf(new LinkedHashSet<>(noPoint)), FuelRouteOptimiser.STRAIGHT_LINE.equals(r.method()) ? NOTE_STRAIGHT : NOTE_FALLBACK);
    }
}
