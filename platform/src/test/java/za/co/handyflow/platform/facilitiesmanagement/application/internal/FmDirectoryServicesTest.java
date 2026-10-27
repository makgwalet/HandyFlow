package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmTechnicianRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** FM vendor and technician records: audit, client-restricted callers, open-work guard, "me". */
class FmDirectoryServicesTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID me = UUID.randomUUID();
    private final Map<UUID, FmVendor> vendors = new LinkedHashMap<>();
    private final Map<UUID, FmTechnician> techs = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private long openWork;
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> f) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object r = f.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FmVendorRepository vendorRepo = fake(FmVendorRepository.class, (n, a) -> switch (n) {
        case "save" -> { FmVendor v = (FmVendor) a[0]; vendors.put(v.getId(), v); yield v; }
        case "findActiveById" -> Optional.ofNullable(vendors.get(a[1])).filter(v -> v.getDeletedAt() == null);
        case "findAllActive" -> new PageImpl<>(vendors.values().stream().filter(v -> v.getDeletedAt() == null).toList());
        default -> NOPE;
    });
    private final FmTechnicianRepository techRepo = fake(FmTechnicianRepository.class, (n, a) -> switch (n) {
        case "save" -> { FmTechnician t = (FmTechnician) a[0]; techs.put(t.getId(), t); yield t; }
        case "findActiveById" -> Optional.ofNullable(techs.get(a[1])).filter(t -> t.getDeletedAt() == null);
        case "findAllActive" -> new PageImpl<>(techs.values().stream().filter(t -> t.getDeletedAt() == null).toList());
        case "existsOtherLinkedToUser" -> techs.values().stream().anyMatch(t -> a[1].equals(t.getLinkedUserId()) && !t.getId().equals(a[2]));
        case "findActiveLinkedToUser" -> techs.values().stream().filter(t -> t.isActive() && a[1].equals(t.getLinkedUserId())).toList();
        default -> NOPE;
    });
    private final za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository docRepo =
            fake(za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository.class, (n, a) -> NOPE);
    private final FmWorkOrderRepository woRepo = fake(FmWorkOrderRepository.class, (n, a) ->
            n.startsWith("countOpenFor") ? (Object) openWork : NOPE);
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action + ":" + details); }
    };
    private final FmVendorService vendorService = new FmVendorService(vendorRepo, woRepo, policy, auditService, docRepo);
    private final FmTechnicianService techService = new FmTechnicianService(techRepo, auditService, woRepo, policy);

    FmDirectoryServicesTest() { TenantContext.setUserId(me.toString()); }

    private UpsertFmVendorRequest vendorReq(String name) {
        return new UpsertFmVendorRequest(name, "PLUMBING", "Sipho", "0821234567", "s@x.co.za", "pays slowly");
    }

    private UpsertFmTechnicianRequest techReq(String name, UUID user) {
        return new UpsertFmTechnicianRequest(name, "0829999999", "t@x.co.za", "HVAC", user);
    }

    @Test
    void everyVendorChangeIsAudited() {
        FmVendorResponse v = vendorService.createVendor(tenant, vendorReq("Pipes Ltd"));
        vendorService.updateVendor(tenant, v.id(), vendorReq("Pipes (Pty) Ltd"));
        vendorService.deactivate(tenant, v.id());
        vendorService.reactivate(tenant, v.id());
        vendorService.deleteVendor(tenant, v.id());
        assertEquals(List.of("VENDOR_CREATED", "VENDOR_UPDATED", "VENDOR_DEACTIVATED", "VENDOR_REACTIVATED", "VENDOR_DELETED"),
                audit.stream().map(s -> s.substring(0, s.indexOf(':'))).toList());
    }

    @Test
    void theVendorAuditTextCarriesNoContactDetails() {
        vendorService.createVendor(tenant, vendorReq("Pipes Ltd"));
        vendorService.updateVendor(tenant, vendors.keySet().iterator().next(), vendorReq("Pipes Ltd"));
        assertTrue(audit.stream().noneMatch(s -> s.contains("0821234567") || s.contains("s@x.co.za") || s.contains("Sipho")));
    }

    @Test
    void aVendorWithUnfinishedWorkCannotBeSwitchedOffOrDeleted() {
        FmVendorResponse v = vendorService.createVendor(tenant, vendorReq("Pipes Ltd"));
        openWork = 2;
        assertEquals("VENDOR_HAS_OPEN_WORK", assertThrows(HandyFlowException.class, () -> vendorService.deactivate(tenant, v.id())).getErrorCode());
        assertEquals("VENDOR_HAS_OPEN_WORK", assertThrows(HandyFlowException.class, () -> vendorService.deleteVendor(tenant, v.id())).getErrorCode());
        assertTrue(vendors.get(v.id()).isActive());
        openWork = 0;
        assertFalse(vendorService.deactivate(tenant, v.id()).active());
    }

    @Test
    void aClientRestrictedCallerSeesNamesOnlyAndCannotChangeVendors() {
        FmVendorResponse v = vendorService.createVendor(tenant, vendorReq("Pipes Ltd"));
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of());
        FmVendorResponse seen = vendorService.getVendors(tenant, null).getContent().get(0);
        assertEquals("Pipes Ltd", seen.companyName());
        assertNull(seen.contactName());
        assertNull(seen.contactPhone());
        assertNull(seen.contactEmail());
        assertNull(seen.notes());
        assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, () -> vendorService.createVendor(tenant, vendorReq("X"))).getErrorCode());
        assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, () -> vendorService.updateVendor(tenant, v.id(), vendorReq("X"))).getErrorCode());
        assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, () -> vendorService.deleteVendor(tenant, v.id())).getErrorCode());
        scope = FmAccessScope.unrestrictedScope();
        assertEquals("0821234567", vendorService.getVendors(tenant, null).getContent().get(0).contactPhone());
    }

    @Test
    void everyTechnicianChangeIsAuditedAndTheLinkChangeStillIs() {
        UUID user = UUID.randomUUID();
        FmTechnicianResponse t = techService.createTechnician(tenant, techReq("Thabo", user));
        techService.updateTechnician(tenant, t.id(), techReq("Thabo M", null));
        techService.deactivate(tenant, t.id());
        techService.reactivate(tenant, t.id());
        techService.deleteTechnician(tenant, t.id());
        List<String> actions = audit.stream().map(s -> s.substring(0, s.indexOf(':'))).toList();
        assertEquals(List.of("TECHNICIAN_CREATED", "TECHNICIAN_LINK_CHANGED", "TECHNICIAN_UPDATED", "TECHNICIAN_LINK_CHANGED",
                "TECHNICIAN_DEACTIVATED", "TECHNICIAN_REACTIVATED", "TECHNICIAN_DELETED"), actions);
    }

    @Test
    void aUserCanBeLinkedToOnlyOneTechnician() {
        UUID user = UUID.randomUUID();
        techService.createTechnician(tenant, techReq("Thabo", user));
        assertThrows(IllegalStateException.class, () -> techService.createTechnician(tenant, techReq("Lerato", user)));
    }

    @Test
    void aTechnicianWithUnfinishedWorkCannotBeSwitchedOffOrDeleted() {
        FmTechnicianResponse t = techService.createTechnician(tenant, techReq("Thabo", null));
        openWork = 1;
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> techService.deactivate(tenant, t.id()));
        assertEquals("TECHNICIAN_HAS_OPEN_WORK", e.getErrorCode());
        assertTrue(e.getMessage().contains("1 unfinished work order;"));
        assertEquals("TECHNICIAN_HAS_OPEN_WORK", assertThrows(HandyFlowException.class, () -> techService.deleteTechnician(tenant, t.id())).getErrorCode());
        openWork = 0;
        assertFalse(techService.deactivate(tenant, t.id()).active());
    }

    @Test
    void aClientRestrictedCallerSeesTechnicianNamesOnlyAndCannotChangeThem() {
        UUID user = UUID.randomUUID();
        FmTechnicianResponse t = techService.createTechnician(tenant, techReq("Thabo", user));
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of());
        FmTechnicianResponse seen = techService.getTechnicians(tenant, null).getContent().get(0);
        assertEquals("Thabo", seen.name());
        assertEquals("HVAC", seen.specialization());
        assertNull(seen.contactPhone());
        assertNull(seen.contactEmail());
        assertNull(seen.linkedUserId());
        assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, () -> techService.updateTechnician(tenant, t.id(), techReq("X", null))).getErrorCode());
        assertEquals("SCOPE_RESTRICTED", assertThrows(HandyFlowException.class, () -> techService.deactivate(tenant, t.id())).getErrorCode());
    }

    @Test
    void meReturnsTheRecordLinkedToTheCallerOrNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> techService.me(tenant));
        techService.createTechnician(tenant, techReq("Other", UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> techService.me(tenant));
        techService.createTechnician(tenant, techReq("Thabo", me));
        FmTechnicianResponse r = techService.me(tenant);
        assertEquals("Thabo", r.name());
        assertEquals("0829999999", r.contactPhone());
    }
}
