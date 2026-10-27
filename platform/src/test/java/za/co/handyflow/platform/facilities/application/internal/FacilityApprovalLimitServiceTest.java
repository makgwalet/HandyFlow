package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityApprovalLimit;
import za.co.handyflow.platform.facilities.domain.repository.FacilityApprovalLimitRepository;
import za.co.handyflow.platform.facilities.dto.ApprovalLimitResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacilityApprovalLimitServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID admin = UUID.randomUUID();
    private final Map<UUID, FacilityApprovalLimit> limits = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();

    private final FacilityApprovalLimitRepository repo = (FacilityApprovalLimitRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityApprovalLimitRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "save" -> { FacilityApprovalLimit l = (FacilityApprovalLimit) a[0]; limits.put(l.getUserId(), l); yield l; }
                case "delete" -> { limits.remove(((FacilityApprovalLimit) a[0]).getUserId()); yield null; }
                case "findForUser" -> Optional.ofNullable(limits.get(a[1]));
                case "findAllForTenant" -> new ArrayList<>(limits.values());
                case "toString" -> "repo";
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action + ":" + details); }
    };
    private final FacilityApprovalLimitService service = new FacilityApprovalLimitService(repo, auditService);

    @Test
    void settingCreatesThenChangesAndAuditsBothWithTheOldValue() {
        TenantContext.setUserId(admin.toString());
        UUID user = UUID.randomUUID();
        ApprovalLimitResponse r = service.set(tenant, user, new BigDecimal("5000"));
        assertEquals(0, new BigDecimal("5000.00").compareTo(r.maxOrderValue()));
        assertEquals(admin, r.setByUserId());
        service.set(tenant, user, new BigDecimal("20000"));
        assertEquals(1, limits.size());
        assertTrue(audit.get(0).contains("none -> 5000.00"));
        assertTrue(audit.get(1).contains("5000.00 -> 20000.00"));
    }

    @Test
    void aNegativeLimitIsRefused() {
        TenantContext.setUserId(admin.toString());
        assertThrows(IllegalArgumentException.class, () -> service.set(tenant, UUID.randomUUID(), new BigDecimal("-1")));
        assertTrue(limits.isEmpty());
    }

    @Test
    void removingDeletesTheLimitAndAuditsIt() {
        TenantContext.setUserId(admin.toString());
        UUID user = UUID.randomUUID();
        service.set(tenant, user, new BigDecimal("100"));
        service.remove(tenant, user);
        assertTrue(limits.isEmpty());
        assertTrue(audit.get(1).startsWith("APPROVAL_LIMIT_REMOVED"));
        assertThrows(ResourceNotFoundException.class, () -> service.remove(tenant, user));
    }

    @Test
    void listReturnsEveryLimit() {
        TenantContext.setUserId(admin.toString());
        service.set(tenant, UUID.randomUUID(), new BigDecimal("1"));
        service.set(tenant, UUID.randomUUID(), new BigDecimal("2"));
        assertEquals(2, service.list(tenant).size());
    }
}
