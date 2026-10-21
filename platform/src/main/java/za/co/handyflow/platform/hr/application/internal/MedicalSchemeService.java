package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrMedicalSchemeMembership;
import za.co.handyflow.platform.hr.domain.model.MedicalSchemeRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrMedicalSchemeMembershipRepository;
import za.co.handyflow.platform.hr.dto.MedicalSchemeRequest;
import za.co.handyflow.platform.hr.dto.MedicalSchemeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Medical scheme membership with effective dates: the source of the medical scheme fees tax credit. */
@Service
@RequiredArgsConstructor
public class MedicalSchemeService {

    private final HrMedicalSchemeMembershipRepository memberships;
    private final HrEmployeeRepository employees;

    @Transactional(readOnly = true)
    public List<MedicalSchemeResponse> list(TenantId tenantId, UUID employeeId) {
        requireEmployee(tenantId, employeeId);
        LocalDate today = LocalDate.now();
        return memberships.forEmployee(tenantId, employeeId).stream().map(m -> toResponse(m, today)).toList();
    }

    /** Adds a membership. The one still open is closed the day before; history is never rewritten. */
    @Transactional
    public MedicalSchemeResponse add(TenantId tenantId, UUID employeeId, MedicalSchemeRequest req, UUID userId) {
        requireEmployee(tenantId, employeeId);
        List<HrMedicalSchemeMembership> existing = memberships.forEmployee(tenantId, employeeId);
        LocalDate openFrom = MedicalSchemeRules.checkNew(
                existing.stream().map(HrMedicalSchemeMembership::toCover).toList(), req.effectiveFrom(), req.dependants());
        if (openFrom != null) {
            existing.stream().filter(m -> m.getEffectiveTo() == null && m.getEffectiveFrom().equals(openFrom))
                    .forEach(m -> { m.closeOn(req.effectiveFrom().minusDays(1)); memberships.save(m); });
        }
        HrMedicalSchemeMembership created = memberships.save(HrMedicalSchemeMembership.create(tenantId, employeeId,
                req.schemeName().trim(), blankToNull(req.memberNumber()), req.dependants(), req.effectiveFrom(),
                blankToNull(req.notes()), userId));
        return toResponse(created, LocalDate.now());
    }

    /** People covered on {@code day} for each employee, from the membership on record or, failing that, their contribution. */
    @Transactional(readOnly = true)
    public Map<UUID, MedicalSchemeRules.Members> membersOn(TenantId tenantId, Collection<HrEmployee> staff, LocalDate day) {
        Map<UUID, List<MedicalSchemeRules.Cover>> byEmployee = new HashMap<>();
        if (!staff.isEmpty()) {
            memberships.forEmployees(tenantId, staff.stream().map(HrEmployee::getId).toList())
                    .forEach(m -> byEmployee.computeIfAbsent(m.getEmployeeId(), k -> new java.util.ArrayList<>()).add(m.toCover()));
        }
        Map<UUID, MedicalSchemeRules.Members> out = new HashMap<>();
        for (HrEmployee e : staff) {
            out.put(e.getId(), MedicalSchemeRules.membersOn(byEmployee.getOrDefault(e.getId(), List.of()), day, e.getMedicalAidContribution()));
        }
        return out;
    }

    private void requireEmployee(TenantId tenantId, UUID employeeId) {
        employees.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
    }

    private MedicalSchemeResponse toResponse(HrMedicalSchemeMembership m, LocalDate today) {
        boolean current = !m.getEffectiveFrom().isAfter(today) && (m.getEffectiveTo() == null || !m.getEffectiveTo().isBefore(today));
        return new MedicalSchemeResponse(m.getId(), m.getEmployeeId(), m.getSchemeName(), m.getMemberNumber(), m.getDependants(),
                1 + m.getDependants(), m.getEffectiveFrom(), m.getEffectiveTo(), current, m.getNotes(), m.getCreatedAt());
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
