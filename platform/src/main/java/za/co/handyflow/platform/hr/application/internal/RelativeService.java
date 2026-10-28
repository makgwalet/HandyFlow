package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeRelative;
import za.co.handyflow.platform.hr.domain.model.RelativeRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRelativeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.ProfileDtos.RelativeRequest;
import za.co.handyflow.platform.hr.dto.ProfileDtos.RelativeView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Next of kin and dependants. HR (with access to personal data) keeps them for anyone; an employee keeps their own through the "mine" calls,
 * which find the employee from the login. The audit trail records that a change was made, never the person's details.
 */
@Service
@RequiredArgsConstructor
public class RelativeService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrEmployeeRelativeRepository repo;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;

    @Transactional(readOnly = true)
    public List<RelativeView> forEmployee(TenantId tenantId, UUID employeeId) {
        employee(tenantId, employeeId);
        LocalDate today = LocalDate.now(ZONE);
        return repo.forEmployee(tenantId, employeeId).stream().map(r -> view(r, today)).toList();
    }

    @Transactional
    public RelativeView add(TenantId tenantId, UUID employeeId, RelativeRequest req, UUID actor) {
        requireActor(actor);
        employee(tenantId, employeeId);
        List<HrEmployeeRelative> existing = repo.forEmployee(tenantId, employeeId);
        RelativeRules.Details d = details(req);
        check(d, existing, null);
        HrEmployeeRelative saved = repo.save(HrEmployeeRelative.of(tenantId.getValue(), employeeId, d, actor));
        audit.record(tenantId, employeeId, "RELATIVE_ADDED", label(saved), actor);
        return view(saved, LocalDate.now(ZONE));
    }

    @Transactional
    public RelativeView update(TenantId tenantId, UUID employeeId, UUID id, RelativeRequest req, UUID actor) {
        requireActor(actor);
        HrEmployeeRelative r = repo.findOne(tenantId, employeeId, id).orElseThrow(() -> new ResourceNotFoundException("Relative", id.toString()));
        RelativeRules.Details d = details(req);
        check(d, repo.forEmployee(tenantId, employeeId), id);
        r.change(d);
        audit.record(tenantId, employeeId, "RELATIVE_CHANGED", label(r), actor);
        return view(repo.save(r), LocalDate.now(ZONE));
    }

    @Transactional
    public void remove(TenantId tenantId, UUID employeeId, UUID id, UUID actor) {
        requireActor(actor);
        HrEmployeeRelative r = repo.findOne(tenantId, employeeId, id).orElseThrow(() -> new ResourceNotFoundException("Relative", id.toString()));
        r.remove(actor);
        repo.save(r);
        audit.record(tenantId, employeeId, "RELATIVE_REMOVED", label(r), actor);
    }

    /** The employee linked to this login, if there is one. */
    @Transactional(readOnly = true)
    public Optional<UUID> employeeOf(TenantId tenantId, UUID userId) {
        return userId == null ? Optional.empty() : employeeRepo.findByUserId(tenantId, userId).map(HrEmployee::getId);
    }

    private void check(RelativeRules.Details d, List<HrEmployeeRelative> existing, UUID editing) {
        int others = (int) existing.stream().filter(r -> !r.getId().equals(editing)).count();
        String problem = RelativeRules.problem(d, LocalDate.now(ZONE), others);
        if (problem == null)
            problem = RelativeRules.sharesProblem(existing.stream().map(r -> new RelativeRules.Share(r.getId(), r.isBeneficiary(), r.getBeneficiaryPercent())).toList(), editing, d);
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    private static RelativeRules.Details details(RelativeRequest q) {
        return RelativeRules.clean(q.kind(), q.fullName(), q.relationship(), q.phone(), q.email(), q.dateOfBirth(), q.address(), q.beneficiary(), q.beneficiaryPercent(), q.notes());
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private static String label(HrEmployeeRelative r) { return (RelativeRules.NEXT_OF_KIN.equals(r.getKind()) ? "next of kin" : "dependant"); }

    static RelativeView view(HrEmployeeRelative r, LocalDate today) {
        return new RelativeView(r.getId(), r.getEmployeeId(), r.getKind(), r.getFullName(), r.getRelationship(), RelativeRules.relationshipLabel(r.getRelationship()),
                r.getPhone(), r.getEmail(), r.getDateOfBirth(), RelativeRules.age(r.getDateOfBirth(), today), r.getAddress(), r.isBeneficiary(),
                r.getBeneficiaryPercent(), r.getNotes(), r.getUpdatedAt());
    }
}
