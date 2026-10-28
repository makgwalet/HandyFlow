package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.EmployeeCreatedEvent;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HrService {

    private final HrEmployeeRepository     employeeRepo;
    private final ErCaseService             erCases;
    private final EmployeeNumberGenerator   numberGen;
    // FIX: backlog 3.3 — publishes EmployeeCreatedEvent so contracting can
    // auto-draft a BCEA employment contract. See EmployeeCreatedEvent's own
    // Javadoc for the full reasoning.
    private final ApplicationEventPublisher eventPublisher;
    private final EmployeeAuditService audit;
    private final LeaveService leaveService;
    private final TerminationFollowUp termination;

    // ── Employees ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getEmployees(TenantId tenantId, String status,
                                               String search, Pageable pageable) {
        return employeeRepo.findAllActive(tenantId, status, search, pageable)
                .map(this::toEmployeeResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id)
                .map(this::toEmployeeResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    @Transactional
    public EmployeeResponse createEmployee(TenantId tenantId, CreateEmployeeRequest req) {
        validateNew(tenantId, req);
        String number = numberGen.next(tenantId);
        HrEmployee emp = HrEmployee.create(
                tenantId, number,
                req.firstName(), req.lastName(),
                req.startDate(), req.employmentType(),
                req.grossSalary(), req.payFrequency());

        // Direct setter calls — no reflection, no silent failures
        emp.setIdNumber(req.idNumber());
        emp.setTaxNumber(req.taxNumber());
        emp.setDateOfBirth(req.dateOfBirth());
        emp.setGender(req.gender());
        emp.setRace(EmployeeRules.normaliseRace(req.race()));
        emp.setEmail(req.email());
        emp.setPhone(req.phone());
        emp.setJobTitle(req.jobTitle());
        emp.setDepartment(req.department());
        emp.setBankName(req.bankName());
        emp.setBankAccountNumber(req.bankAccountNumber());
        emp.setBankBranchCode(req.bankBranchCode());
        emp.setMedicalAidContribution(req.medicalAidContribution() != null
                ? req.medicalAidContribution() : BigDecimal.ZERO);
        emp.setPensionContribution(req.pensionContribution() != null
                ? req.pensionContribution() : BigDecimal.ZERO);
        emp.setTravelAllowance(req.travelAllowance() != null
                ? req.travelAllowance() : BigDecimal.ZERO);
        emp.setEmergencyContactName(req.emergencyContactName());
        emp.setEmergencyContactPhone(req.emergencyContactPhone());
        emp.setEmergencyContactRelation(blankToNull(req.emergencyContactRelation()));
        emp.setNotes(req.notes());

        employeeRepo.save(emp);
        audit.record(tenantId, emp.getId(), "CREATED", null, null);
        log.info("Created employee={} {} tenant={}", number, emp.getFullName(), tenantId);

        // FIX: backlog 3.3 — this was the one missing piece. The
        // eventPublisher field, EmployeeCreatedEvent, and
        // ContractingHrEventHandler were all already correctly in place;
        // this publish call — the thing that actually fires the event —
        // was the part that never got added, twice confirmed missing on
        // direct inspection. Last statement before the return,
        // deliberately: a failure anywhere earlier in employee creation
        // must never result in an event firing for an employee that
        // doesn't actually exist.
        eventPublisher.publishEvent(EmployeeCreatedEvent.of(
                tenantId, emp.getId(), emp.getFirstName(), emp.getLastName(),
                emp.getIdNumber(), emp.getJobTitle(), emp.getStartDate(), emp.getGrossSalary()));

        return toEmployeeResponse(emp);
    }

    /**
     * Kept for callers that pass a full {@link CreateEmployeeRequest} (HrFacade). It no longer touches pay or bank details:
     * those change only through {@link EmployeeChangeService}. Personal data is not changed here either.
     */
    @Transactional
    public EmployeeResponse updateEmployee(TenantId tenantId, UUID id, CreateEmployeeRequest req) {
        return updateDetails(tenantId, id, new UpdateEmployeeRequest(req.email(), req.phone(), req.employmentType(), req.jobTitle(),
                req.department(), req.emergencyContactName(), req.emergencyContactPhone(), req.notes(),
                null, null, null, null, null), false, null);
    }

    /** Edits working details. Personal data is applied only when {@code sensitiveAllowed}. A null field is left alone. */
    @Transactional
    public EmployeeResponse updateDetails(TenantId tenantId, UUID id, UpdateEmployeeRequest req, boolean sensitiveAllowed, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        LocalDate today = LocalDate.now();
        List<String> changed = new ArrayList<>();

        fail(EmployeeRules.emailProblem(req.email()));
        fail(EmployeeRules.phoneProblem(req.phone()));
        fail(EmployeeRules.phoneProblem(req.emergencyContactPhone()));
        if (req.email() != null)                 { emp.setEmail(blankToNull(req.email()));          changed.add("email"); }
        if (req.phone() != null)                 { emp.setPhone(blankToNull(req.phone()));          changed.add("phone"); }
        if (req.employmentType() != null)        { emp.setEmploymentType(req.employmentType());     changed.add("employment type"); }
        if (req.jobTitle() != null)              { emp.setJobTitle(blankToNull(req.jobTitle()));    changed.add("job title"); }
        if (req.department() != null)            { emp.setDepartment(blankToNull(req.department())); changed.add("department"); }
        if (req.notes() != null)                 { emp.setNotes(req.notes());                       changed.add("notes"); }

        if (sensitiveAllowed) {
            String idNumber = req.idNumber() != null ? EmployeeRules.normaliseId(req.idNumber()) : emp.getIdNumber();
            LocalDate dob = req.dateOfBirth() != null ? req.dateOfBirth() : emp.getDateOfBirth();
            if (req.idNumber() != null) {
                fail(EmployeeRules.idProblem(idNumber, today));
                if (idNumber != null && !idNumber.isBlank() && employeeRepo.countByIdNumber(tenantId, idNumber, id) > 0) {
                    throw new IllegalStateException("Another employee already has this ID number.");
                }
                emp.setIdNumber(blankToNull(idNumber));
                changed.add("ID number");
            }
            if (req.idNumber() != null || req.dateOfBirth() != null) {
                fail(EmployeeRules.dobProblem(idNumber, dob, emp.getStartDate(), today));
                if (req.dateOfBirth() != null) { emp.setDateOfBirth(req.dateOfBirth()); changed.add("date of birth"); }
            }
            if (req.taxNumber() != null) {
                fail(EmployeeRules.taxNumberProblem(req.taxNumber()));
                emp.setTaxNumber(blankToNull(req.taxNumber()));
                changed.add("tax number");
            }
            if (req.gender() != null)                { emp.setGender(blankToNull(req.gender()));    changed.add("gender"); }
            if (req.race() != null)                  { emp.setRace(blankToNull(req.race()));        changed.add("race"); }
            if (req.emergencyContactName() != null)  { emp.setEmergencyContactName(blankToNull(req.emergencyContactName())); changed.add("emergency contact"); }
            if (req.emergencyContactPhone() != null) { emp.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone())); changed.add("emergency contact"); }
        }
        employeeRepo.save(emp);
        if (!changed.isEmpty()) audit.record(tenantId, id, "UPDATED", String.join(", ", new LinkedHashSet<>(changed)), actor);
        log.info("Updated employee={}", emp.getEmployeeNumber());
        return toEmployeeResponse(emp);
    }

    /** Terminate with the last day worked and a reason. Leave after the last day is cancelled and portal access is closed. */
    @Transactional
    public EmployeeResponse terminateEmployee(TenantId tenantId, UUID id, LocalDate endDate, String reason, UUID actor) {
        return terminateEmployee(tenantId, id, endDate, reason, null, null, actor).employee();
    }

    /** What recording a termination gave back: the employee as saved and what followed from it. */
    public record Terminated(EmployeeResponse employee, List<String> followUp) {}

    /** As above, also keeping how employment ended and when notice was given, and reporting what followed. */
    @Transactional
    public Terminated terminateEmployee(TenantId tenantId, UUID id, LocalDate endDate, String reason, String endType, LocalDate noticeGivenOn, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        fail(EmployeeRules.terminationProblem(emp.getStatus(), emp.getStartDate(), endDate, reason, LocalDate.now()));
        fail(NoticeRules.problem(endType, noticeGivenOn, endDate, emp.getStartDate()));
        emp.terminate(endDate, reason, actor, endType, noticeGivenOn);
        employeeRepo.save(emp);
        audit.record(tenantId, id, "TERMINATED", "last day " + endDate + ", " + NoticeRules.typeLabel(endType), actor);
        List<String> followUp = termination.run(tenantId, emp, actor);
        log.info("Terminated employee={}", emp.getEmployeeNumber());
        return new Terminated(toEmployeeResponse(emp), followUp);
    }

    @Transactional
    public EmployeeResponse setManager(TenantId tenantId, UUID id, UUID managerId, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        Map<UUID, UUID> managerOf = new HashMap<>();
        Map<UUID, HrEmployee> byId = new HashMap<>();
        for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) {
            byId.put(e.getId(), e);
            managerOf.put(e.getId(), e.getManagerId());   // a person at the top maps to null but is still a valid manager
        }
        HrEmployee manager = managerId == null ? null : byId.get(managerId);
        if (managerId != null && manager == null) throw new IllegalArgumentException("The manager must be a current employee.");
        fail(OrgRules.managerProblem(id, managerId, managerOf));
        emp.setManagerId(managerId);
        employeeRepo.save(emp);
        audit.record(tenantId, id, "MANAGER_CHANGED", manager == null ? "removed" : manager.getFullName(), actor);
        return toEmployeeResponse(emp);
    }

    @Transactional
    public EmployeeResponse linkUser(TenantId tenantId, UUID id, UUID userId, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        if (userId != null) {
            Optional<HrEmployee> other = employeeRepo.findByUserId(tenantId, userId);
            if (other.isPresent() && !other.get().getId().equals(id)) {
                throw new IllegalStateException("That login is already linked to " + other.get().getFullName() + ".");
            }
        }
        emp.setUserId(userId);
        employeeRepo.save(emp);
        audit.record(tenantId, id, userId == null ? "LOGIN_UNLINKED" : "LOGIN_LINKED", null, actor);
        return toEmployeeResponse(emp);
    }

    private void validateNew(TenantId tenantId, CreateEmployeeRequest req) {
        LocalDate today = LocalDate.now();
        String id = EmployeeRules.normaliseId(req.idNumber());
        fail(EmployeeRules.idProblem(id, today));
        fail(EmployeeRules.dobProblem(id, req.dateOfBirth(), req.startDate(), today));
        fail(EmployeeRules.taxNumberProblem(req.taxNumber()));
        fail(EmployeeRules.emailProblem(req.email()));
        fail(EmployeeRules.phoneProblem(req.phone()));
        fail(EmployeeRules.phoneProblem(req.emergencyContactPhone()));
        fail(EmployeeRules.raceProblem(req.race()));
        fail(EmployeeRules.relationProblem(req.emergencyContactName(), req.emergencyContactRelation()));
        fail(EmployeeRules.bankProblem(req.bankName(), req.bankAccountNumber(), req.bankBranchCode()));
        fail(EmployeeChangeRules.compensationProblem(req.grossSalary(), req.travelAllowance(), req.medicalAidContribution(),
                req.pensionContribution(), req.payFrequency()));
        if (id != null && !id.isBlank() && employeeRepo.countByIdNumber(tenantId, id, null) > 0) {
            throw new IllegalStateException("An employee with this ID number already exists.");
        }
    }

    private static void fail(String problem) {
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ── Leave: see LeaveService ───────────────────────────────────────────────

    @Transactional
    public List<LeaveBalanceResponse> getLeaveBalances(TenantId tenantId, UUID employeeId, int year) {
        return leaveService.balances(tenantId, employeeId, year);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getLeaveRequests(TenantId tenantId, String status, Pageable pageable) {
        return leaveService.requests(tenantId, status, pageable);
    }

    @Transactional
    public LeaveRequestResponse submitLeaveRequest(TenantId tenantId, UUID employeeId, SubmitLeaveRequest req) {
        return leaveService.submit(tenantId, employeeId, req);
    }

    @Transactional(readOnly = true)
    public List<OrgChartNodeResponse> getOrgChart(TenantId tenantId) {
        return employeeRepo.findAllActive(tenantId, null, null, Pageable.unpaged())
                .getContent().stream()
                .map(e -> new OrgChartNodeResponse(
                        e.getId(), e.getFullName(), e.getJobTitle(), e.getDepartment(), e.getManagerId()))
                .toList();
    }

    // ── Disciplinary ──────────────────────────────────────────────────────────

    /**
     * Older entry points to discipline, kept for the Security referral and for callers of the earlier endpoints. They now open and read
     * employee relations cases ({@link ErCaseService}), so a case opened here follows the same process as any other.
     */
    @Transactional(readOnly = true)
    public List<DisciplinaryResponse> getDisciplinary(TenantId tenantId, UUID employeeId) {
        findActive(tenantId, employeeId);
        return erCases.disciplinaryCases(tenantId, employeeId)
                .stream().map(d -> toDisciplinaryResponse(d, tenantId)).toList();
    }

    /**
     * Opens a disciplinary case with the matter described, as reported by someone: nothing is decided and no hearing is set. The incident
     * type is now the case title (it used to be a fixed list that the database rejected for most values). A hearing date is ignored,
     * because a hearing needs a notice with charges, which has its own step.
     */
    @Transactional
    public DisciplinaryResponse addDisciplinary(TenantId tenantId, UUID employeeId,
                                                AddDisciplinaryRequest req,
                                                UUID issuedBy) {
        HrEmployee emp = findActive(tenantId, employeeId);
        HrErCase d = erCases.openDisciplinary(tenantId, employeeId, req.incidentType(), req.description(), req.incidentDate(), issuedBy);
        log.info("Disciplinary case {} opened for employee={}", d.getCaseNumber(), emp.getEmployeeNumber());
        return toDisciplinaryResponse(d, tenantId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HrEmployee findActive(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private void applyOptionalFields(HrEmployee emp, CreateEmployeeRequest req) {
        try {
            java.lang.reflect.Field[] fields = emp.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                f.setAccessible(true);
                switch (f.getName()) {
                    case "idNumber"    -> f.set(emp, req.idNumber());
                    case "taxNumber"   -> f.set(emp, req.taxNumber());
                    case "dateOfBirth" -> f.set(emp, req.dateOfBirth());
                    case "gender"      -> f.set(emp, req.gender());
                    case "race"        -> f.set(emp, req.race());
                    case "email"       -> f.set(emp, req.email());
                    case "phone"       -> f.set(emp, req.phone());
                    case "jobTitle"    -> f.set(emp, req.jobTitle());
                    case "department"  -> f.set(emp, req.department());
                    case "bankName"    -> f.set(emp, req.bankName());
                    case "bankAccountNumber" -> f.set(emp, req.bankAccountNumber());
                    case "bankBranchCode"    -> f.set(emp, req.bankBranchCode());
                    case "medicalAidContribution" ->
                            f.set(emp, req.medicalAidContribution() != null
                                    ? req.medicalAidContribution() : BigDecimal.ZERO);
                    case "pensionContribution" ->
                            f.set(emp, req.pensionContribution() != null
                                    ? req.pensionContribution() : BigDecimal.ZERO);
                    case "travelAllowance" ->
                            f.set(emp, req.travelAllowance() != null
                                    ? req.travelAllowance() : BigDecimal.ZERO);
                    case "emergencyContactName"     -> f.set(emp, req.emergencyContactName());
                    case "emergencyContactPhone"    -> f.set(emp, req.emergencyContactPhone());
                    case "notes"       -> f.set(emp, req.notes());
                }
            }
        } catch (Exception e) {
            log.warn("Could not apply optional fields: {}", e.getMessage());
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    /** The employee record linked to a login, if there is one. Used by other modules for "my own" screens. */
    @Transactional(readOnly = true)
    public java.util.Optional<EmployeeResponse> findEmployeeByUserId(TenantId tenantId, UUID userId) {
        if (userId == null) return java.util.Optional.empty();
        return employeeRepo.findByUserId(tenantId, userId).map(this::toEmployeeResponse);
    }

    /** Everyone not yet terminated, as other modules may see them (with reporting line and login link). */
    @Transactional(readOnly = true)
    public java.util.List<za.co.handyflow.platform.hr.EmployeeSummary> notTerminatedSummaries(TenantId tenantId) {
        return employeeRepo.findNotTerminated(tenantId).stream().map(za.co.handyflow.platform.hr.EmployeeSummary::of).toList();
    }

    /** One employee with reporting line and login link, if they still exist. */
    @Transactional(readOnly = true)
    public java.util.Optional<za.co.handyflow.platform.hr.EmployeeSummary> findSummary(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).map(za.co.handyflow.platform.hr.EmployeeSummary::of);
    }

    private EmployeeResponse toEmployeeResponse(HrEmployee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeNumber(),
                e.getFirstName(), e.getLastName(), e.getFullName(),
                e.getIdNumber(), e.getTaxNumber(), e.getDateOfBirth(),
                e.getGender(), e.getRace(), e.getEmail(), e.getPhone(),
                e.getEmploymentType(), e.getJobTitle(), e.getDepartment(),
                e.getStartDate(), e.getEndDate(), e.getStatus(),
                e.getGrossSalary(), e.getPayFrequency(),
                e.getMedicalAidContribution(), e.getPensionContribution(),
                e.getTravelAllowance(), e.getEmergencyContactName(),
                e.getEmergencyContactPhone(), e.getCreatedAt());
    }

    private DisciplinaryResponse toDisciplinaryResponse(HrErCase d,
                                                        TenantId tenantId) {
        String empName = employeeRepo.findActiveById(tenantId, d.getEmployeeId())
                .map(HrEmployee::getFullName).orElse("Unknown");
        return new DisciplinaryResponse(d.getId(), d.getEmployeeId(), empName,
                d.getIncidentDate() != null ? d.getIncidentDate() : d.getReportedOn(), d.getTitle(), d.getDescription(),
                d.getOutcome(), d.getHearingDate(), d.isAcknowledged(), d.getCreatedAt());
    }
}
