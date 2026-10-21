package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeAudit;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeAuditRepository;
import za.co.handyflow.platform.hr.dto.EmployeeAuditResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** The trail of what was done to an employee record. Records field names and actions, never personal or pay values. */
@Service
@RequiredArgsConstructor
public class EmployeeAuditService {

    private final HrEmployeeAuditRepository repo;

    @Transactional
    public void record(TenantId tenantId, UUID employeeId, String action, String detail, UUID actor) {
        repo.save(HrEmployeeAudit.of(tenantId.getValue(), employeeId, action, detail, actor));
    }

    /**
     * Records that someone looked at personal or pay data, at most once an hour per person and record, so the trail
     * shows who has been looking without one line for every screen refresh.
     */
    @Transactional
    public void recordViewOnce(TenantId tenantId, UUID employeeId, String action, UUID actor) {
        if (actor == null) return;
        long recent = repo.countSince(tenantId, employeeId, actor, action, Instant.now().minus(1, ChronoUnit.HOURS));
        if (recent == 0) record(tenantId, employeeId, action, null, actor);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAuditResponse> history(TenantId tenantId, UUID employeeId) {
        return repo.forEmployee(tenantId, employeeId).stream()
                .map(a -> new EmployeeAuditResponse(a.getId(), a.getAction(), a.getDetail(), a.getActorId(), a.getCreatedAt()))
                .toList();
    }
}
