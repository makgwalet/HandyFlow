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
    private final HrDisciplinaryRepository  disciplinaryRepo;
    private final EmployeeNumberGenerator   numberGen;
    // FIX: backlog 3.3 — publishes EmployeeCreatedEvent so contracting can
    // auto-draft a BCEA employment contract. See EmployeeCreatedEvent's own
    // Javadoc for the full reasoning.
    private final ApplicationEventPublisher eventPublisher;
    private final EmployeeAuditService audit;
    private final LeaveService leaveService;

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
        emp.setRace(req.race());
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

    /** Terminate with the last day worked and a reason. The portal access of the person is left as it is. */
    @Transactional
    public EmployeeResponse terminateEmployee(TenantId tenantId, UUID id, LocalDate endDate, String reason, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        fail(EmployeeRules.terminationProblem(emp.getStatus(), emp.getStartDate(), endDate, reason, LocalDate.now()));
        emp.terminate(endDate, reason, actor);
        employeeRepo.save(emp);
        audit.record(tenantId, id, "TERMINATED", "last day " + endDate, actor);
        log.info("Terminated employee={}", emp.getEmployeeNumber());
        return toEmployeeResponse(emp);
    }

    @Transactional
    public EmployeeResponse setManager(TenantId tenantId, UUID id, UUID managerId, UUID actor) {
        HrEmployee emp = findActive(tenantId, id);
        Map<UUID, UUID> managerOf = new HashMap<>();
        Map<UUID, HrEmployee> byId = new HashMap<>();
        for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) {
            byId.put(e.getId(), e);
            if (e.getManagerId() != null) managerOf.put(e.getId(), e.getManagerId());
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

    @Transactional(readOnly = true)
    public List<DisciplinaryResponse> getDisciplinary(TenantId tenantId, UUID employeeId) {
        findActive(tenantId, employeeId);
        return disciplinaryRepo.findByEmployee(employeeId)
                .stream().map(d -> toDisciplinaryResponse(d, tenantId)).toList();
    }

    @Transactional
    public DisciplinaryResponse addDisciplinary(TenantId tenantId, UUID employeeId,
                                                AddDisciplinaryRequest req,
                                                UUID issuedBy) {
        HrEmployee emp = findActive(tenantId, employeeId);
        HrDisciplinary d = HrDisciplinary.create(tenantId, employeeId,
                req.incidentDate(), req.incidentType(), req.description(), issuedBy);
        if (req.hearingDate() != null) d.setOutcome(null, req.hearingDate());
        disciplinaryRepo.save(d);
        log.info("Disciplinary {} added for employee={}", req.incidentType(),
                emp.getEmployeeNumber());
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

    private DisciplinaryResponse toDisciplinaryResponse(HrDisciplinary d,
                                                        TenantId tenantId) {
        String empName = employeeRepo.findActiveById(tenantId, d.getEmployeeId())
                .map(HrEmployee::getFullName).orElse("Unknown");
        return new DisciplinaryResponse(d.getId(), d.getEmployeeId(), empName,
                d.getIncidentDate(), d.getIncidentType(), d.getDescription(),
                d.getOutcome(), d.getHearingDate(), d.isAcknowledged(), d.getCreatedAt());
    }
}