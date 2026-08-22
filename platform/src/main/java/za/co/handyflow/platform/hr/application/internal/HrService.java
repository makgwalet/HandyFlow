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
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HrService {

    private final HrEmployeeRepository     employeeRepo;
    private final HrLeaveBalanceRepository  balanceRepo;
    private final HrLeaveRequestRepository  leaveRepo;
    private final HrDisciplinaryRepository  disciplinaryRepo;
    private final EmployeeNumberGenerator   numberGen;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    // FIX: backlog 3.3 — publishes EmployeeCreatedEvent so contracting can
    // auto-draft a BCEA employment contract. See EmployeeCreatedEvent's own
    // Javadoc for the full reasoning.
    private final ApplicationEventPublisher eventPublisher;

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
        seedLeaveBalances(tenantId, emp.getId(), LocalDate.now().getYear());
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

    @Transactional
    public EmployeeResponse updateEmployee(TenantId tenantId, UUID id,
                                           CreateEmployeeRequest req) {
        HrEmployee emp = findActive(tenantId, id);
        emp.setEmail(req.email());
        emp.setPhone(req.phone());
        emp.setJobTitle(req.jobTitle());
        emp.setDepartment(req.department());
        emp.setGrossSalary(req.grossSalary());
        emp.setPayFrequency(req.payFrequency());
        emp.setTravelAllowance(req.travelAllowance() != null ? req.travelAllowance() : BigDecimal.ZERO);
        emp.setMedicalAidContribution(req.medicalAidContribution() != null ? req.medicalAidContribution() : BigDecimal.ZERO);
        emp.setPensionContribution(req.pensionContribution() != null ? req.pensionContribution() : BigDecimal.ZERO);
        emp.setBankName(req.bankName());
        emp.setBankAccountNumber(req.bankAccountNumber());
        emp.setBankBranchCode(req.bankBranchCode());
        emp.setEmergencyContactName(req.emergencyContactName());
        emp.setEmergencyContactPhone(req.emergencyContactPhone());
        emp.setNotes(req.notes());
        employeeRepo.save(emp);
        log.info("Updated employee={}", emp.getEmployeeNumber());
        return toEmployeeResponse(emp);
    }

    @Transactional
    public EmployeeResponse terminateEmployee(TenantId tenantId, UUID id,
                                              LocalDate endDate) {
        HrEmployee emp = findActive(tenantId, id);
        emp.terminate(endDate);
        employeeRepo.save(emp);
        log.info("Terminated employee={}", emp.getEmployeeNumber());
        return toEmployeeResponse(emp);
    }

    // ── Leave balances ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getLeaveBalances(TenantId tenantId,
                                                       UUID employeeId, int year) {
        findActive(tenantId, employeeId); // verify employee belongs to tenant
        return balanceRepo.findByEmployeeAndYear(employeeId, year)
                .stream().map(this::toBalanceResponse).toList();
    }

    // ── Leave requests ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getLeaveRequests(TenantId tenantId,
                                                       String status, Pageable pageable) {
        return leaveRepo.findAllByTenant(tenantId, status, pageable)
                .map(r -> toLeaveResponse(r, tenantId));
    }

    @Transactional
    public LeaveRequestResponse submitLeaveRequest(TenantId tenantId,
                                                   UUID employeeId,
                                                   SubmitLeaveRequest req) {
        HrEmployee emp = findActive(tenantId, employeeId);

        long workingDays = req.startDate().datesUntil(req.endDate().plusDays(1))
                .filter(d -> d.getDayOfWeek().getValue() <= 5)
                .count();
        BigDecimal days = BigDecimal.valueOf(workingDays);

        // Check balance
        int year = req.startDate().getYear();
        balanceRepo.findByEmployeeYearAndType(employeeId, year, req.leaveType())
                .ifPresent(bal -> {
                    if (bal.getAvailableDays().compareTo(days) < 0)
                        throw new IllegalArgumentException(
                                "Insufficient " + req.leaveType() + " leave balance. " +
                                        "Available: " + bal.getAvailableDays() + " days, requested: " + days);
                    bal.addPending(days);
                    balanceRepo.save(bal);
                });

        HrLeaveRequest request = HrLeaveRequest.create(tenantId, employeeId,
                req.leaveType(), req.startDate(), req.endDate(), days, req.reason());
        leaveRepo.save(request);
        log.info("Leave request {} days {} for employee={}", days, req.leaveType(),
                emp.getEmployeeNumber());
        notifyApprover(tenantId, emp, request);
        return toLeaveResponse(request, tenantId);
    }

    @Transactional
    public LeaveRequestResponse approveLeaveRequest(TenantId tenantId,
                                                    UUID requestId, UUID approverId) {
        HrLeaveRequest req = leaveRepo.findByTenantAndId(tenantId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId.toString()));
        if (!"PENDING".equals(req.getStatus()))
            throw new IllegalStateException("Only PENDING requests can be approved");

        req.approve(approverId);
        leaveRepo.save(req);

        balanceRepo.findByEmployeeYearAndType(
                        req.getEmployeeId(), req.getStartDate().getYear(), req.getLeaveType())
                .ifPresent(bal -> {
                    bal.approvePending(req.getDaysRequested());
                    balanceRepo.save(bal);
                });
        employeeRepo.findActiveById(tenantId, req.getEmployeeId()).ifPresent(emp ->
                notifyEmployeeLeaveDecision(tenantId, emp, req, NotificationType.LEAVE_REQUEST_APPROVED,
                        "Leave request approved",
                        "Your " + req.getLeaveType().toLowerCase() + " leave request ("
                                + req.getStartDate() + " to " + req.getEndDate() + ") has been approved."));
        return toLeaveResponse(req, tenantId);
    }

    @Transactional
    public LeaveRequestResponse rejectLeaveRequest(TenantId tenantId, UUID requestId,
                                                   UUID approverId, String reason) {
        HrLeaveRequest req = leaveRepo.findByTenantAndId(tenantId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId.toString()));
        req.reject(approverId, reason);
        leaveRepo.save(req);

        balanceRepo.findByEmployeeYearAndType(
                        req.getEmployeeId(), req.getStartDate().getYear(), req.getLeaveType())
                .ifPresent(bal -> {
                    bal.rejectPending(req.getDaysRequested());
                    balanceRepo.save(bal);
                });
        employeeRepo.findActiveById(tenantId, req.getEmployeeId()).ifPresent(emp ->
                notifyEmployeeLeaveDecision(tenantId, emp, req, NotificationType.LEAVE_REQUEST_REJECTED,
                        "Leave request rejected",
                        "Your " + req.getLeaveType().toLowerCase() + " leave request ("
                                + req.getStartDate() + " to " + req.getEndDate() + ") was rejected."
                                + (reason != null && !reason.isBlank() ? " Reason: " + reason : "")));
        return toLeaveResponse(req, tenantId);
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

    // ── Leave balance seeder ──────────────────────────────────────────────────

    private void seedLeaveBalances(TenantId tenantId, UUID employeeId, int year) {
        // WHY? BCEA statutory minimums — every employee gets these automatically
        record Seed(String type, BigDecimal days) {}
        List.of(
                new Seed("ANNUAL",               new BigDecimal("15")),  // BCEA min 15 days
                new Seed("SICK",                 new BigDecimal("30")),  // 30 days per 3-year cycle
                new Seed("FAMILY_RESPONSIBILITY",new BigDecimal("3")),   // 3 days BCEA
                new Seed("STUDY",                new BigDecimal("5"))
        ).forEach(s -> {
            if (balanceRepo.findByEmployeeYearAndType(employeeId, year, s.type()).isEmpty()) {
                balanceRepo.save(HrLeaveBalance.create(
                        tenantId, employeeId, year, s.type(), s.days()));
            }
        });
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

    // HrEmployee.managerId is the approver if set and resolvable; otherwise
    // falls back to tenant admins — same fallback shape as Expenses'
    // notifySubmitted(), for the same reason (no guaranteed single owner
    // of the event).
    private void notifyApprover(TenantId tenantId, HrEmployee emp, HrLeaveRequest request) {
        List<Recipient> recipients;
        if (emp.getManagerId() != null) {
            recipients = employeeRepo.findActiveById(tenantId, emp.getManagerId())
                    .filter(mgr -> mgr.getEmail() != null && !mgr.getEmail().isBlank())
                    .map(mgr -> List.of(Recipient.external(mgr.getFullName(), mgr.getEmail(), mgr.getPhone())))
                    .orElse(null);
        } else {
            recipients = null;
        }
        if (recipients == null || recipients.isEmpty()) {
            recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        }
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.LEAVE_REQUEST_SUBMITTED)
                .title("Leave request: " + emp.getFullName())
                .message(emp.getFullName() + " requested " + request.getDaysRequested() + " day(s) "
                        + request.getLeaveType().toLowerCase() + " leave ("
                        + request.getStartDate() + " to " + request.getEndDate() + ") — needs approval.")
                .actionUrl("/hr/leave-requests")
                .sourceModule("hr")
                .sourceEntityId(request.getId().toString())
                .recipients(recipients)
                .build());
    }

    // HrEmployee is not necessarily a platform user — its own email/phone
    // columns are the only contact details this module can rely on, so this
    // is Recipient.external(), never Recipient.user(). Skipped silently if
    // the employee record has no email, same "no contact, no crash" shape
    // FleetNotificationScheduler uses for drivers.
    private void notifyEmployeeLeaveDecision(TenantId tenantId, HrEmployee emp, HrLeaveRequest request,
                                             NotificationType type, String title, String message) {
        if (emp.getEmail() == null || emp.getEmail().isBlank()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/hr/leave-requests")
                .sourceModule("hr")
                .sourceEntityId(request.getId().toString())
                .recipient(Recipient.external(emp.getFullName(), emp.getEmail(), emp.getPhone()))
                .build());
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

    private LeaveBalanceResponse toBalanceResponse(HrLeaveBalance b) {
        return new LeaveBalanceResponse(b.getId(), b.getLeaveType(), b.getLeaveYear(),
                b.getEntitledDays(), b.getTakenDays(), b.getPendingDays(),
                b.getAvailableDays());
    }

    private LeaveRequestResponse toLeaveResponse(HrLeaveRequest r, TenantId tenantId) {
        String empName = employeeRepo.findActiveById(tenantId, r.getEmployeeId())
                .map(HrEmployee::getFullName).orElse("Unknown");
        return new LeaveRequestResponse(r.getId(), r.getEmployeeId(), empName,
                r.getLeaveType(), r.getStartDate(), r.getEndDate(),
                r.getDaysRequested(), r.getReason(), r.getStatus(),
                r.getRejectionReason(), r.getCreatedAt());
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