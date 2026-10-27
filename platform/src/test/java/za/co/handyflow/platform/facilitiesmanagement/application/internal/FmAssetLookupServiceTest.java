package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPpmSchedule;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPpmScheduleRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetLookupResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FmAssetLookupServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID clientId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final FmAsset asset = FmAsset.create(tenant, siteId, "GEN-0042", "Generator", "GENERATOR", "Roof",
            null, null, null, null, null, "HIGH", null);
    private final List<FmAsset> assets = new ArrayList<>(List.of(asset));
    private final List<FmWorkOrder> openOrders = new ArrayList<>();
    private final List<FmPpmSchedule> schedules = new ArrayList<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FmAssetRepository assetRepo = fake(FmAssetRepository.class, (n, a) -> switch (n) {
        case "findActiveById" -> assets.stream().filter(x -> x.getId().equals(a[1])).findFirst();
        case "findActiveByTag" -> assets.stream().filter(x -> x.getAssetTag().equals(a[1])).toList();
        default -> NOPE;
    });
    private final FmWorkOrderRepository woRepo = fake(FmWorkOrderRepository.class, (n, a) ->
            "findOpenForAsset".equals(n) ? List.copyOf(openOrders) : NOPE);
    private final FmPpmScheduleRepository ppmRepo = fake(FmPpmScheduleRepository.class, (n, a) ->
            "findByAsset".equals(n) ? List.copyOf(schedules) : NOPE);
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
    };
    private final FmAssetLookupService service = new FmAssetLookupService(assetRepo, woRepo, ppmRepo, policy);

    private FmWorkOrder order(String number) {
        return FmWorkOrder.create(tenant, number, clientId, siteId, asset.getId(), null, "CORRECTIVE", "HIGH", "Won't start", "Sipho", LocalDate.now());
    }

    @Test
    void findsByQrPayloadIdAndTagAlike() {
        assertEquals(asset.getId(), service.lookup(tenant, "HF:FMASSET:" + asset.getId()).asset().id());
        assertEquals(asset.getId(), service.lookup(tenant, asset.getId().toString()).asset().id());
        FmAssetLookupResponse r = service.lookup(tenant, " GEN-0042 ");
        assertEquals(asset.getId(), r.asset().id());
        assertEquals("HF:FMASSET:" + asset.getId(), r.qrPayload());
    }

    @Test
    void unknownCodesAndOutOfScopeSitesLookLikeNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, "NOPE"));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, UUID.randomUUID().toString()));
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, "GEN-0042"));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(tenant, asset.getId().toString()));
        assertThrows(ResourceNotFoundException.class, () -> service.qrPayload(tenant, asset.getId()));
    }

    @Test
    void aTagSharedByTwoVisibleAssetsIsRefusedRatherThanGuessed() {
        assets.add(FmAsset.create(tenant, siteId, "GEN-0042", "Other generator", "GENERATOR", "Basement", null, null, null, null, null, "LOW", null));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.lookup(tenant, "GEN-0042"));
        assertTrue(e.getMessage().contains("QR label"));
        // ...but scanning the label (the id) is unambiguous
        assertEquals(asset.getId(), service.lookup(tenant, "HF:FMASSET:" + asset.getId()).asset().id());
    }

    @Test
    void aDuplicateTagAtAnotherClientsSiteDoesNotLeakIntoThisScan() {
        UUID otherSite = UUID.randomUUID();
        assets.add(FmAsset.create(tenant, otherSite, "GEN-0042", "Their generator", "GENERATOR", "Roof", null, null, null, null, null, "LOW", null));
        scope = FmAccessScope.restrictedToClients(List.of(clientId), List.of(siteId));
        assertEquals(asset.getId(), service.lookup(tenant, "GEN-0042").asset().id());
    }

    @Test
    void showsOpenWorkOrdersAndTheNextPpm() {
        openOrders.add(order("WO-1"));
        openOrders.add(order("WO-2"));
        schedules.add(FmPpmSchedule.create(tenant, asset.getId(), "Service", null, 90, LocalDate.now().plusDays(40)));
        schedules.add(FmPpmSchedule.create(tenant, asset.getId(), "Fuel check", null, 30, LocalDate.now().plusDays(12)));
        FmAssetLookupResponse r = service.lookup(tenant, "GEN-0042");
        assertEquals(2, r.openWorkOrders().size());
        assertEquals(LocalDate.now().plusDays(12), r.nextPpmDue());
    }

    @Test
    void aTechnicianOnlySeesTheirOwnJobsAmongTheOpenOnes() {
        UUID me = UUID.randomUUID();
        FmWorkOrder mine = order("WO-1");
        mine.assign(me, "Me", null, null);
        FmWorkOrder theirs = order("WO-2");
        theirs.assign(UUID.randomUUID(), "Other", null, null);
        openOrders.addAll(List.of(mine, theirs));
        scope = FmAccessScope.assignedJobs(List.of(siteId), List.of(me));
        FmAssetLookupResponse r = service.lookup(tenant, "GEN-0042");
        assertEquals(List.of("WO-1"), r.openWorkOrders().stream().map(FmAssetLookupResponse.OpenWorkOrder::workOrderNumber).toList());
    }

    @Test
    void openWorkOrdersAreCapped() {
        for (int i = 0; i < 15; i++) openOrders.add(order("WO-" + i));
        assertEquals(FmAssetLookupService.MAX_OPEN_WORK_ORDERS, service.lookup(tenant, "GEN-0042").openWorkOrders().size());
    }

    @Test
    void anInactiveScheduleDoesNotCountTowardsNextPpm() {
        FmPpmSchedule off = FmPpmSchedule.create(tenant, asset.getId(), "Old", null, 30, LocalDate.now().plusDays(1));
        off.deactivate();
        schedules.add(off);
        assertNull(service.lookup(tenant, "GEN-0042").nextPpmDue());
    }
}
