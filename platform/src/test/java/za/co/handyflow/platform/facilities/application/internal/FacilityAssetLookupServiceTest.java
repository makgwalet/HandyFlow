package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.AssetLookupResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacilityAssetLookupServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID siteId = UUID.randomUUID();
    private final FacilityAsset asset = FacilityAsset.create(tenant, siteId, "GEN-0042", "Generator", "GENERATOR", "Roof",
            null, null, null, null, null, "HIGH", null);
    private final List<FacilityWorkOrder> openOrders = new ArrayList<>();
    private final List<FacilityPpmSchedule> schedules = new ArrayList<>();
    private final List<FacilityInspection> inspections = new ArrayList<>();
    private long openActions = 0;
    private AccessScope scope = AccessScope.unrestricted();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) -> switch (n) {
        case "findActiveById" -> Optional.ofNullable(asset.getId().equals(a[1]) ? asset : null);
        case "findActiveByTag" -> Optional.ofNullable("GEN-0042".equals(a[1]) ? asset : null);
        default -> NOPE;
    });
    private final FacilityWorkOrderRepository woRepo = fake(FacilityWorkOrderRepository.class, (n, a) ->
            "findOpenForAsset".equals(n) ? List.copyOf(openOrders) : NOPE);
    private final FacilityPpmScheduleRepository ppmRepo = fake(FacilityPpmScheduleRepository.class, (n, a) ->
            "findByAsset".equals(n) ? List.copyOf(schedules) : NOPE);
    private final FacilityInspectionRepository inspectionRepo = fake(FacilityInspectionRepository.class, (n, a) ->
            "findLatestCompletedForAsset".equals(n) ? List.copyOf(inspections) : NOPE);
    private final FacilityCorrectiveActionRepository actionRepo = fake(FacilityCorrectiveActionRepository.class, (n, a) ->
            "countUnfinishedForAsset".equals(n) ? openActions : NOPE);
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityAssetLookupService service = new FacilityAssetLookupService(assetRepo, woRepo, ppmRepo, inspectionRepo, actionRepo, policy);

    private FacilityWorkOrder order(String number) {
        return FacilityWorkOrder.create(tenant, number, siteId, asset.getId(), null, "CORRECTIVE", "HIGH", "Won't start", "Sipho", LocalDate.now());
    }

    @Test
    void findsByQrPayloadIdAndTagAlike() {
        assertEquals(asset.getId(), service.lookup(tenant, "HF:ASSET:" + asset.getId()).asset().id());
        assertEquals(asset.getId(), service.lookup(tenant, asset.getId().toString()).asset().id());
        AssetLookupResponse r = service.lookup(tenant, " GEN-0042 ");
        assertEquals(asset.getId(), r.asset().id());
        assertEquals("HF:ASSET:" + asset.getId(), r.qrPayload());
    }

    @Test
    void unknownCodesAndOutOfScopeSitesLookLikeNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, "NOPE"));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, UUID.randomUUID().toString()));
        scope = AccessScope.restrictedToSites(List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, "GEN-0042"));
        assertThrows(ResourceNotFoundException.class, () -> service.qrPayload(tenant, asset.getId()));
    }

    @Test
    void showsOpenWorkOrdersNextPpmLastInspectionAndOpenActions() {
        openOrders.add(order("WO-1"));
        openOrders.add(order("WO-2"));
        schedules.add(FacilityPpmSchedule.create(tenant, asset.getId(), "Service", null, 90, LocalDate.now().plusDays(40)));
        schedules.add(FacilityPpmSchedule.create(tenant, asset.getId(), "Fuel check", null, 30, LocalDate.now().plusDays(12)));
        openActions = 3;
        AssetLookupResponse r = service.lookup(tenant, "GEN-0042");
        assertEquals(2, r.openWorkOrders().size());
        assertEquals(LocalDate.now().plusDays(12), r.nextPpmDue());
        assertEquals(3, r.openCorrectiveActions());
        assertNull(r.lastInspection());
    }

    @Test
    void aTechnicianOnlySeesTheirOwnJobsAmongTheOpenOnes() {
        UUID me = UUID.randomUUID();
        FacilityWorkOrder mine = order("WO-1");
        mine.assign(me, "Me", null, null);
        FacilityWorkOrder theirs = order("WO-2");
        theirs.assign(UUID.randomUUID(), "Other", null, null);
        openOrders.addAll(List.of(mine, theirs));
        scope = AccessScope.assignedJobs(List.of(siteId), List.of(me));
        AssetLookupResponse r = service.lookup(tenant, "GEN-0042");
        assertEquals(List.of("WO-1"), r.openWorkOrders().stream().map(AssetLookupResponse.OpenWorkOrder::workOrderNumber).toList());
    }

    @Test
    void openWorkOrdersAreCapped() {
        for (int i = 0; i < 15; i++) openOrders.add(order("WO-" + i));
        assertEquals(FacilityAssetLookupService.MAX_OPEN_WORK_ORDERS, service.lookup(tenant, "GEN-0042").openWorkOrders().size());
    }

    @Test
    void inactiveScheduleDoesNotCountTowardsNextPpm() {
        FacilityPpmSchedule off = FacilityPpmSchedule.create(tenant, asset.getId(), "Old", null, 30, LocalDate.now().plusDays(1));
        off.deactivate();
        schedules.add(off);
        assertNull(service.lookup(tenant, "GEN-0042").nextPpmDue());
    }
}
