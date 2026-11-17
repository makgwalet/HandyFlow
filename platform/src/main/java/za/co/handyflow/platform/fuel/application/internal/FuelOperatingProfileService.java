package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelDivisionActivation;
import za.co.handyflow.platform.fuel.domain.model.FuelOperatingProfile;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/** Setup of a tenant's Fuel operation: progress through the wizard, activating and deactivating divisions. History is never deleted. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelOperatingProfileService {

    private final FuelOperatingProfileRepository profiles;
    private final FuelDivisionActivationRepository activations;
    private final FuelDivisionGuard guard;
    private final FuelLegalEntityRepository legalEntities;
    private final FuelTankRepository tanks;
    private final FuelTankerRepository tankers;
    private final FuelPumpRepository pumps;
    private final FuelShiftRepository shifts;
    private final FuelDeliveryRepository deliveries;
    private final FuelSalesOrderRepository orders;
    private final FuelReservationRepository reservations;
    private final FuelPurchaseOrderRepository purchaseOrders;
    private final FuelTruckStopStayRepository truckStopStays;
    private final FuelTruckStopService truckStop;
    private final za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository truckStopTabs;

    @Transactional(readOnly = true)
    public ProfileView view(TenantId tenantId) {
        Optional<FuelOperatingProfile> p = profiles.findByTenant(tenantId);
        Set<FuelDivision> active = guard.activeDivisions(tenantId);
        Map<FuelDivision, FuelDivisionActivation> rows = new EnumMap<>(FuelDivision.class);
        for (FuelDivisionActivation a : activations.findByTenant(tenantId)) rows.put(a.getDivision(), a);
        List<DivisionView> divisions = new ArrayList<>();
        for (FuelDivision d : FuelDivision.values()) {
            FuelDivisionActivation a = rows.get(d);
            divisions.add(new DivisionView(d, FuelDivisionRules.label(d), active.contains(d),
                    a == null ? null : a.getActivatedAt(), a == null ? null : a.getDeactivatedAt(), a == null ? null : a.getDeactivationReason()));
        }
        String status = p.map(FuelOperatingProfile::getStatus).orElse("NOT_STARTED");
        return new ProfileView(status, p.map(FuelOperatingProfile::isLegacyMode).orElse(false), p.map(FuelOperatingProfile::getBusinessSize).orElse(null),
                p.map(FuelOperatingProfile::getOrgStructure).orElse(null), p.map(FuelOperatingProfile::getWizardStep).orElse(0),
                p.map(FuelOperatingProfile::getWizardAnswers).orElse(null), List.copyOf(active), divisions, checklist(tenantId, p.orElse(null), active));
    }

    @Transactional
    public ProfileView saveWizard(TenantId tenantId, WizardRequest req) {
        FuelOperatingProfile p = profiles.findByTenant(tenantId).orElseGet(() -> FuelOperatingProfile.forTenant(tenantId));
        p.saveProgress(req.businessSize(), req.orgStructure(), req.wizardStep(), req.wizardAnswers());
        profiles.save(p);
        return view(tenantId);
    }

    /** Completes setup with exactly this set of divisions. Anything the tenant could use before and is leaving out must have no open work. */
    @Transactional
    public ProfileView activate(TenantId tenantId, List<FuelDivision> selectedList, UUID by) {
        if (selectedList == null || selectedList.isEmpty()) throw new IllegalArgumentException("Choose at least one division.");
        Set<FuelDivision> selected = EnumSet.copyOf(selectedList);
        Set<FuelDivision> current = guard.activeDivisions(tenantId);
        Set<FuelDivision> leaving = EnumSet.noneOf(FuelDivision.class);
        leaving.addAll(current); leaving.removeAll(selected);
        failIfBlocked(tenantId, leaving, selected);

        Map<FuelDivision, FuelDivisionActivation> rows = new EnumMap<>(FuelDivision.class);
        for (FuelDivisionActivation a : activations.findByTenant(tenantId)) rows.put(a.getDivision(), a);
        for (FuelDivision d : FuelDivision.values()) {
            FuelDivisionActivation a = rows.get(d);
            if (selected.contains(d)) {
                if (a == null) activations.save(FuelDivisionActivation.of(tenantId, d, by));
                else if (!a.isActive()) { a.activate(by); activations.save(a); }
            } else if (a != null && a.isActive()) {
                a.deactivate(by, "Not selected when setup was completed");
                activations.save(a);
            }
        }
        FuelOperatingProfile p = profiles.findByTenant(tenantId).orElseGet(() -> FuelOperatingProfile.forTenant(tenantId));
        p.activate(by);
        profiles.save(p);
        log.info("Fuel setup completed tenant={} divisions={} by={}", tenantId, selected, by);
        return view(tenantId);
    }

    @Transactional
    public ProfileView activateDivision(TenantId tenantId, FuelDivision division, UUID by) {
        requireSetupDone(tenantId);
        Optional<FuelDivisionActivation> row = activations.findByTenant(tenantId).stream().filter(a -> a.getDivision() == division).findFirst();
        if (row.isPresent() && row.get().isActive()) return view(tenantId); // already on: idempotent
        if (row.isPresent()) { row.get().activate(by); activations.save(row.get()); }
        else activations.save(FuelDivisionActivation.of(tenantId, division, by));
        log.info("Fuel division activated tenant={} division={} by={}", tenantId, division, by);
        return view(tenantId);
    }

    @Transactional
    public ProfileView deactivateDivision(TenantId tenantId, FuelDivision division, String reason, UUID by) {
        requireSetupDone(tenantId);
        Set<FuelDivision> active = guard.activeDivisions(tenantId);
        if (!active.contains(division)) return view(tenantId); // already off: idempotent
        Set<FuelDivision> after = EnumSet.copyOf(active); after.remove(division);
        if (after.isEmpty()) throw new IllegalStateException("A company must keep at least one division active.");
        failIfBlocked(tenantId, EnumSet.of(division), after);
        FuelDivisionActivation a = activations.findByTenant(tenantId).stream().filter(x -> x.getDivision() == division).findFirst().orElseThrow();
        a.deactivate(by, reason);
        activations.save(a);
        log.info("Fuel division deactivated tenant={} division={} by={} reason={}", tenantId, division, by, reason);
        return view(tenantId);
    }

    private void requireSetupDone(TenantId tenantId) {
        FuelOperatingProfile p = profiles.findByTenant(tenantId).orElse(null);
        if (p == null || !p.isActive() || p.isLegacyMode()) {
            throw new IllegalStateException("Finish the Fuel setup first (choose your divisions and activate), then divisions can be turned on or off one at a time.");
        }
    }

    private void failIfBlocked(TenantId tenantId, Set<FuelDivision> leaving, Set<FuelDivision> after) {
        List<String> blockers = blockers(tenantId, leaving, after);
        if (!blockers.isEmpty()) throw new IllegalStateException("Cannot turn this off yet: " + String.join("; ", blockers) + ".");
    }

    /**
     * Open work that a division being switched off would strand. Orders, deliveries and reservations are shared by Wholesale and
     * Distribution, so they only block when neither remains; purchase orders block when neither Wholesale nor Retail remains.
     * Pending approvals are not checked (the Approvals module is not queried from here).
     */
    List<String> blockers(TenantId tenantId, Set<FuelDivision> leaving, Set<FuelDivision> after) {
        List<String> out = new ArrayList<>();
        boolean commercialStays = after.contains(FuelDivision.WHOLESALE_SUPPLY) || after.contains(FuelDivision.DISTRIBUTION);
        boolean procurementStays = after.contains(FuelDivision.WHOLESALE_SUPPLY) || after.contains(FuelDivision.RETAIL_FORECOURT);
        if (leaving.contains(FuelDivision.RETAIL_FORECOURT)) {
            long open = shifts.countOpen(tenantId);
            if (open > 0) out.add(open + " forecourt shift(s) are still open");
        }
        if ((leaving.contains(FuelDivision.WHOLESALE_SUPPLY) || leaving.contains(FuelDivision.DISTRIBUTION)) && !commercialStays) {
            long openDeliveries = deliveries.countOpen(tenantId);
            if (openDeliveries > 0) out.add(openDeliveries + " delivery(ies) are scheduled or on the road");
            long openOrders = orders.countByStatus(tenantId, FuelSalesOrder.CONFIRMED) + orders.countByStatus(tenantId, FuelSalesOrder.PART_DELIVERED);
            if (openOrders > 0) out.add(openOrders + " confirmed order(s) are not fully delivered");
            int live = reservations.findLive(tenantId, Instant.now()).size();
            if (live > 0) out.add(live + " stock reservation(s) are still active");
        }
        if (leaving.contains(FuelDivision.TRUCK_STOP)) {
            long stays = truckStopStays.countOpen(tenantId);
            if (stays > 0) out.add(stays + " overnight stay(s) are booked or in the yard");
            int reserved = truckStop.openReservationCount(tenantId);
            if (reserved > 0) out.add(reserved + " truck stop reservation(s) are still open");
            long openTabs = truckStopTabs.countOpen(tenantId);
            if (openTabs > 0) out.add(openTabs + " truck stop tab(s) are still open");
        }
        if (leaving.contains(FuelDivision.WHOLESALE_SUPPLY) && !procurementStays) {
            long po = purchaseOrders.countAwaitingDelivery(tenantId);
            if (po > 0) out.add(po + " purchase order(s) are waiting for delivery");
        }
        return out;
    }

    private List<ChecklistItem> checklist(TenantId tenantId, FuelOperatingProfile p, Set<FuelDivision> active) {
        List<ChecklistItem> out = new ArrayList<>();
        out.add(new ChecklistItem("divisions", "Choose and activate your divisions", p != null && p.isActive()));
        out.add(new ChecklistItem("legal-entity", "Add your legal entity (or entities)", !legalEntities.findAllActive(tenantId).isEmpty()));
        out.add(new ChecklistItem("tanks", "Add a depot and tanks", !tanks.findAllActive(tenantId).isEmpty()));
        if (active.contains(FuelDivision.DISTRIBUTION)) out.add(new ChecklistItem("tankers", "Add your tankers", !tankers.findAllActive(tenantId).isEmpty()));
        if (active.contains(FuelDivision.RETAIL_FORECOURT)) out.add(new ChecklistItem("pumps", "Add your pumps", !pumps.findAllActive(tenantId).isEmpty()));
        return out;
    }
}
