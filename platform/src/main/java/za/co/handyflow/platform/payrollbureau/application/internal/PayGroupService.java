package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.PayGroup;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.PayPeriodRules;
import za.co.handyflow.platform.payrollbureau.dto.PayGroupDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pay groups of a client: frequency, period calendar and which employees belong (phase 1, slice 1c). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayGroupService {

    private final PayGroupRepository groupRepo;
    private final PayClientRepository clientRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayRunRepository payRunRepo;

    @Transactional(readOnly = true)
    public List<PayGroupResponse> list(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return groupRepo.findByClient(tenantId.getValue(), clientId).stream().map(g -> toResponse(g, clientId)).toList();
    }

    @Transactional
    public PayGroupResponse create(TenantId tenantId, UUID clientId, CreatePayGroupRequest req) {
        requireClient(tenantId, clientId);
        int lag = req.payLagDays() == null ? 0 : req.payLagDays();
        String frequency = req.frequency() == null ? null : req.frequency().trim().toUpperCase();
        String problem = PayPeriodRules.groupProblem(req.name(), frequency, req.anchorDate(), lag);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PAY_GROUP");
        PayGroup g = PayGroup.create(tenantId.getValue(), clientId, req.name(), frequency,
                "MONTHLY".equals(frequency) ? null : req.anchorDate(), lag);
        try {
            groupRepo.saveAndFlush(g);
        } catch (DataIntegrityViolationException e) {
            throw new HandyFlowException("This client already has an active pay group with that name.",
                    HttpStatus.CONFLICT, "PAY_GROUP_EXISTS");
        }
        return toResponse(g, clientId);
    }

    @Transactional
    public PayGroupResponse archive(TenantId tenantId, UUID groupId) {
        PayGroup g = load(tenantId, groupId);
        if (!employeeRepo.findActiveByGroup(g.getPayClientId(), groupId).isEmpty()) {
            throw new HandyFlowException("Move this group's employees to another group before archiving it.",
                    HttpStatus.CONFLICT, "PAY_GROUP_IN_USE");
        }
        boolean openRun = payRunRepo.findByClient(tenantId.getValue(), g.getPayClientId(), org.springframework.data.domain.Pageable.unpaged())
                .stream().anyMatch(r -> groupId.equals(r.getPayGroupId()) && "DRAFT".equals(r.getStatus()));
        if (openRun) {
            throw new HandyFlowException("This group has a draft pay run. Finish or remove it first.",
                    HttpStatus.CONFLICT, "PAY_GROUP_IN_USE");
        }
        g.archive();
        groupRepo.save(g);
        return toResponse(g, g.getPayClientId());
    }

    /** Moves an employee into a group, or back to the client's default payroll when {@code groupId} is null. */
    @Transactional
    public void assignEmployee(TenantId tenantId, UUID clientId, UUID employeeId, UUID groupId) {
        requireClient(tenantId, clientId);
        PayEmployee e = employeeRepo.findActiveById(tenantId.getValue(), employeeId)
                .filter(x -> x.getPayClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", employeeId.toString()));
        if (groupId != null) {
            PayGroup g = load(tenantId, groupId);
            if (!g.getPayClientId().equals(clientId) || !g.isActive()) {
                throw new HandyFlowException("That pay group is not available for this client.",
                        HttpStatus.BAD_REQUEST, "INVALID_PAY_GROUP");
            }
        }
        e.assignToGroup(groupId);
        employeeRepo.save(e);
    }

    /** The next period to run for a group: the one after its latest run, or the one containing {@code today} if it has none. */
    @Transactional(readOnly = true)
    public NextPeriodResponse nextPeriod(TenantId tenantId, UUID groupId, LocalDate today) {
        PayGroup g = load(tenantId, groupId);
        LocalDate lastEnd = payRunRepo.findByClient(tenantId.getValue(), g.getPayClientId(), org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(r -> groupId.equals(r.getPayGroupId())).map(PayRun::getPeriodEnd)
                .max(Comparator.naturalOrder()).orElse(null);
        PayPeriodRules.Period p = lastEnd == null
                ? PayPeriodRules.periodContaining(g.getFrequency(), g.getAnchorDate(), today)
                : PayPeriodRules.periodAfter(g.getFrequency(), g.getAnchorDate(), lastEnd);
        LocalDate payDate = PayPeriodRules.payDateFor(p, g.getPayLagDays());
        return new NextPeriodResponse(p.start(), p.end(), payDate, PayPeriodRules.taxYearOfPayDate(payDate));
    }

    public PayGroup load(TenantId tenantId, UUID groupId) {
        return groupRepo.findByTenantAndId(tenantId.getValue(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("PayGroup", groupId.toString()));
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        clientRepo.findActiveById(tenantId.getValue(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", clientId.toString()));
    }

    private PayGroupResponse toResponse(PayGroup g, UUID clientId) {
        int count = employeeRepo.findActiveByGroup(clientId, g.getId()).size();
        return new PayGroupResponse(g.getId(), g.getName(), g.getFrequency(), g.getAnchorDate(), g.getPayLagDays(),
                g.getStatus(), count);
    }
}
