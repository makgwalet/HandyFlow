package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.BenefitRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeBenefit;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeBenefitRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.BenefitDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Taxable employer-paid benefits per employee. Whoever may prepare payroll may add or end one, because it changes the tax on the next run;
 * each line says who added it and why, and is never edited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrEmployeeBenefitRepository repo;
    private final HrEmployeeRepository employeeRepo;

    @Transactional(readOnly = true)
    public List<View> forEmployee(TenantId tenantId, UUID employeeId) {
        employee(tenantId, employeeId);
        LocalDate today = LocalDate.now(ZONE);
        return repo.forEmployee(tenantId, employeeId).stream().map(b -> view(b, today)).toList();
    }

    @Transactional
    public View add(TenantId tenantId, UUID employeeId, AddRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        List<BenefitRules.Line> existing = repo.forEmployee(tenantId, employeeId).stream().map(HrEmployeeBenefit::asLine).toList();
        String problem = BenefitRules.addProblem(req.kind(), req.monthlyAmount(), req.effectiveFrom(), req.effectiveTo(), req.description(), req.reason(),
                existing, emp.getStartDate());
        if (problem != null) throw new IllegalArgumentException(problem);
        HrEmployeeBenefit saved = repo.save(HrEmployeeBenefit.of(tenantId.getValue(), employeeId, req.kind(), req.monthlyAmount().setScale(2, RoundingMode.HALF_UP),
                req.effectiveFrom(), req.effectiveTo(), req.description(), req.reason(), actor));
        log.info("Benefit {} {} added for employee={} by user={}", saved.getKind(), saved.getMonthlyAmount(), employeeId, actor);
        return view(saved, LocalDate.now(ZONE));
    }

    @Transactional
    public View end(TenantId tenantId, UUID benefitId, EndRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployeeBenefit b = repo.findByTenantAndId(tenantId, benefitId).orElseThrow(() -> new ResourceNotFoundException("Benefit", benefitId.toString()));
        String problem = BenefitRules.endProblem(b.getEffectiveFrom(), b.getEffectiveTo(), req.lastDay());
        if (problem != null) throw new IllegalArgumentException(problem);
        b.end(req.lastDay(), actor);
        return view(repo.save(b), LocalDate.now(ZONE));
    }

    /** What applies to each employee on the last day of a pay period, read once for the whole run. */
    @Transactional(readOnly = true)
    public Map<UUID, BenefitRules.Monthly> forRun(TenantId tenantId, LocalDate periodEnd) {
        Map<UUID, java.util.List<BenefitRules.Line>> byEmployee = new HashMap<>();
        for (HrEmployeeBenefit b : repo.activeOn(tenantId, periodEnd)) byEmployee.computeIfAbsent(b.getEmployeeId(), k -> new java.util.ArrayList<>()).add(b.asLine());
        Map<UUID, BenefitRules.Monthly> out = new HashMap<>();
        byEmployee.forEach((id, lines) -> out.put(id, BenefitRules.on(lines, periodEnd)));
        return out;
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private static View view(HrEmployeeBenefit b, LocalDate today) {
        boolean inForce = !today.isBefore(b.getEffectiveFrom()) && (b.getEffectiveTo() == null || !today.isAfter(b.getEffectiveTo()));
        return new View(b.getId(), b.getEmployeeId(), b.getKind(), BenefitRules.label(b.getKind()), b.getMonthlyAmount(), b.getEffectiveFrom(), b.getEffectiveTo(),
                b.getDescription(), b.getReason(), inForce, b.getCreatedAt());
    }
}
