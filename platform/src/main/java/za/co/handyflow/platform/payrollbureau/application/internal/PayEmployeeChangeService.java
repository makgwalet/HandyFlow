package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployeeChangeRequest;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayClientRepository;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayEmployeeChangeRequestRepository;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayEmployeeRepository;
import za.co.handyflow.platform.payrollbureau.domain.rules.EmployeeChangeRules;
import za.co.handyflow.platform.payrollbureau.dto.PayEmployeeChangeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static za.co.handyflow.platform.payrollbureau.domain.rules.EmployeeChangeRules.differs;

/** Holds salary and bank-detail changes until a different user approves them (audit 2026-11-26, D4). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayEmployeeChangeService {

    private final PayEmployeeChangeRequestRepository changeRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayClientRepository clientRepo;

    /** True when the proposed values differ from the employee's current pay or bank details. */
    public boolean touchesControlledFields(PayEmployee e, BigDecimal gross, BigDecimal travel, BigDecimal pension,
                                           BigDecimal medical, String bankName, String bankAccount, String bankBranch) {
        return differs(e.getGrossSalary(), gross) || differs(e.getTravelAllowance(), travel)
                || differs(e.getPensionContribution(), pension) || differs(e.getMedicalAidContribution(), medical)
                || differs(e.getBankName(), bankName) || differs(e.getBankAccountNumber(), bankAccount)
                || differs(e.getBankBranchCode(), bankBranch);
    }

    @Transactional
    public PayEmployeeChangeRequest submit(PayEmployee e, UUID requestedBy, BigDecimal gross, BigDecimal travel,
                                           BigDecimal pension, BigDecimal medical,
                                           String bankName, String bankAccount, String bankBranch) {
        if (changeRepo.findPendingForEmployee(e.getTenantId(), e.getId()).isPresent()) {
            throw new HandyFlowException("This employee already has a change waiting for approval. "
                    + "Approve, reject or cancel it first.", HttpStatus.CONFLICT, "CHANGE_PENDING");
        }
        PayEmployeeChangeRequest r = PayEmployeeChangeRequest.create(e, requestedBy, gross, travel, pension, medical,
                bankName, bankAccount, bankBranch);
        changeRepo.saveAndFlush(r);
        log.info("Payroll bureau change held for approval employee={} request={}", e.getId(), r.getId());
        return r;
    }

    @Transactional(readOnly = true)
    public List<PayEmployeeChangeResponse> list(TenantId tenantId, UUID clientId) {
        clientRepo.findActiveById(tenantId.getValue(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", clientId.toString()));
        return changeRepo.findByClient(tenantId.getValue(), clientId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> pendingEmployeeIds(TenantId tenantId, UUID clientId) {
        return new java.util.HashSet<>(changeRepo.pendingEmployeeIds(tenantId.getValue(), clientId));
    }

    @Transactional
    public PayEmployeeChangeResponse approve(TenantId tenantId, UUID requestId, UUID approver, String note) {
        PayEmployeeChangeRequest r = load(tenantId, requestId);
        problem(EmployeeChangeRules.decisionProblem(r.getStatus(), r.getRequestedBy(), approver));
        PayEmployee e = employeeRepo.findActiveById(tenantId.getValue(), r.getPayEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", r.getPayEmployeeId().toString()));
        boolean stale = differs(e.getGrossSalary(), r.getPrevGrossSalary())
                || differs(e.getTravelAllowance(), r.getPrevTravelAllowance())
                || differs(e.getPensionContribution(), r.getPrevPension())
                || differs(e.getMedicalAidContribution(), r.getPrevMedicalAid())
                || differs(e.getBankName(), r.getPrevBankName())
                || differs(e.getBankAccountNumber(), r.getPrevBankAccount())
                || differs(e.getBankBranchCode(), r.getPrevBankBranch());
        if (stale) {
            throw new HandyFlowException("The employee's details changed after this request was made. "
                    + "Reject it and submit the change again.", HttpStatus.CONFLICT, "CHANGE_STALE");
        }
        e.setGrossSalary(r.getNewGrossSalary());
        e.setTravelAllowance(r.getNewTravelAllowance() != null ? r.getNewTravelAllowance() : BigDecimal.ZERO);
        e.setPensionContribution(r.getNewPension() != null ? r.getNewPension() : BigDecimal.ZERO);
        e.setMedicalAidContribution(r.getNewMedicalAid() != null ? r.getNewMedicalAid() : BigDecimal.ZERO);
        e.setBankDetails(r.getNewBankName(), r.getNewBankAccount(), r.getNewBankBranch());
        employeeRepo.save(e);
        r.decide("APPROVED", approver, note);
        changeRepo.save(r);
        log.info("Payroll bureau change approved request={} by={}", requestId, approver);
        return toResponse(r);
    }

    @Transactional
    public PayEmployeeChangeResponse reject(TenantId tenantId, UUID requestId, UUID approver, String note) {
        PayEmployeeChangeRequest r = load(tenantId, requestId);
        problem(EmployeeChangeRules.decisionProblem(r.getStatus(), r.getRequestedBy(), approver));
        r.decide("REJECTED", approver, note);
        changeRepo.save(r);
        return toResponse(r);
    }

    @Transactional
    public PayEmployeeChangeResponse cancel(TenantId tenantId, UUID requestId, UUID user) {
        PayEmployeeChangeRequest r = load(tenantId, requestId);
        problem(EmployeeChangeRules.cancelProblem(r.getStatus(), r.getRequestedBy(), user));
        r.decide("CANCELLED", user, null);
        changeRepo.save(r);
        return toResponse(r);
    }

    private PayEmployeeChangeRequest load(TenantId tenantId, UUID id) {
        return changeRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployeeChangeRequest", id.toString()));
    }

    private void problem(String message) {
        if (message != null) throw new HandyFlowException(message, HttpStatus.CONFLICT, "CHANGE_NOT_ALLOWED");
    }

    private PayEmployeeChangeResponse toResponse(PayEmployeeChangeRequest r) {
        String name = employeeRepo.findById(r.getPayEmployeeId()).map(PayEmployee::getFullName).orElse("Unknown");
        return new PayEmployeeChangeResponse(r.getId(), r.getPayEmployeeId(), name, r.getStatus(),
                r.getPrevGrossSalary(), r.getNewGrossSalary(), r.getPrevTravelAllowance(), r.getNewTravelAllowance(),
                r.getPrevPension(), r.getNewPension(), r.getPrevMedicalAid(), r.getNewMedicalAid(),
                r.getPrevBankName(), r.getNewBankName(), r.getPrevBankAccount(), r.getNewBankAccount(),
                r.getPrevBankBranch(), r.getNewBankBranch(),
                r.getRequestedBy(), r.getRequestedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }
}
