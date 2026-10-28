package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.CareerRules;
import za.co.handyflow.platform.hr.domain.model.HrCareerEvent;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrCareerEventRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.ProfileDtos.CareerRequest;
import za.co.handyflow.platform.hr.dto.ProfileDtos.CareerView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** The career timeline: read it (pay lines only for those who may see pay), add an earlier job or promotion by hand, remove a hand entry. */
@Service
@RequiredArgsConstructor
public class CareerService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrCareerEventRepository repo;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;

    @Transactional(readOnly = true)
    public List<CareerView> forEmployee(TenantId tenantId, UUID employeeId, boolean includePay) {
        employee(tenantId, employeeId);
        return repo.forEmployee(tenantId, employeeId).stream().filter(c -> includePay || !c.isPayRelated()).map(CareerService::view).toList();
    }

    @Transactional
    public CareerView add(TenantId tenantId, UUID employeeId, CareerRequest req, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrEmployee emp = employee(tenantId, employeeId);
        String problem = CareerRules.manualProblem(req.kind(), req.eventDate(), req.endDate(), req.employer(), req.jobTitle(), req.department(), req.note(),
                emp.getStartDate(), LocalDate.now(ZONE));
        if (problem != null) throw new IllegalArgumentException(problem);
        HrCareerEvent saved = repo.save(HrCareerEvent.of(tenantId.getValue(), employeeId, req.kind(), req.eventDate(), req.endDate(), clean(req.employer()),
                clean(req.jobTitle()), clean(req.department()), null, null, clean(req.note()), false, false, actor));
        audit.record(tenantId, employeeId, "CAREER_ADDED", CareerRules.label(saved.getKind()), actor);
        return view(saved);
    }

    /** Only an entry made by hand can be removed; what the system wrote stays. */
    @Transactional
    public void remove(TenantId tenantId, UUID eventId, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrCareerEvent e = repo.findOne(tenantId, eventId).orElseThrow(() -> new ResourceNotFoundException("CareerEvent", eventId.toString()));
        if (e.isAuto()) throw new IllegalStateException("This line was recorded by the system and cannot be removed.");
        e.remove(actor);
        repo.save(e);
        audit.record(tenantId, e.getEmployeeId(), "CAREER_REMOVED", CareerRules.label(e.getKind()), actor);
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    static CareerView view(HrCareerEvent c) {
        return new CareerView(c.getId(), c.getKind(), CareerRules.label(c.getKind()), c.getEventDate(), c.getEndDate(), c.getEmployer(), c.getJobTitle(),
                c.getDepartment(), c.getFromValue(), c.getToValue(),
                CareerRules.SALARY_CHANGED.equals(c.getKind()) ? CareerRules.percent(c.getFromValue(), c.getToValue()) : null,
                c.getNote(), c.isPayRelated(), c.isAuto(), c.getCreatedAt());
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
