package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.dto.UpsertVendorRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacilityVendorServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final Map<UUID, FacilityVendor> vendors = new HashMap<>();
    private final List<String> audit = new ArrayList<>();

    private final FacilityVendorRepository repo = (FacilityVendorRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityVendorRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "save" -> { FacilityVendor v = (FacilityVendor) a[0]; vendors.put(v.getId(), v); yield v; }
                case "findActiveById" -> Optional.ofNullable(vendors.get(a[1]));
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityVendorService service = new FacilityVendorService(repo, null, new FacilityAccessPolicy(null, null, null), auditService);

    private UUID newVendor() {
        za.co.handyflow.platform.shared.TenantContext.setUserId(UUID.randomUUID().toString());
        return service.createVendor(tenant, new UpsertVendorRequest("ACME Plumbing", "PLUMBING", null, null, null, null)).id();
    }

    @Test
    void deactivatingAndReactivatingAreAudited() {
        UUID id = newVendor();
        assertFalse(service.deactivate(tenant, id).active());
        assertTrue(service.reactivate(tenant, id).active());
        assertEquals(List.of("VENDOR_CREATED", "VENDOR_DEACTIVATED", "VENDOR_REACTIVATED"), audit);
    }

    @Test
    void deletingIsAudited() {
        UUID id = newVendor();
        service.deleteVendor(tenant, id);
        assertEquals(List.of("VENDOR_CREATED", "VENDOR_DELETED"), audit);
    }

    @Test
    void unknownVendorIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.deactivate(tenant, UUID.randomUUID()));
        assertTrue(audit.isEmpty());
    }
}
