package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.PayComponentRules;
import za.co.handyflow.platform.payrollbureau.dto.PayComponentDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The component catalogue, what each employee gets every period, and the one-off items of a pay run (phase 1, slice 1e). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayComponentService {

    private final PayComponentRepository componentRepo;
    private final PayEmployeeComponentRepository employeeComponentRepo;
    private final PayRunItemRepository runItemRepo;
    private final PayEmployeeRepository employeeRepo;
    private final PayRunRepository payRunRepo;

    // ── catalogue ──────────────────────────────────────────────────────────────

    /** The tenant's components. The standard set is made the first time they are asked for. */
    @Transactional
    public List<PayComponentResponse> list(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        List<PayComponent> all = componentRepo.findByTenant(tid);
        if (all.isEmpty()) {
            for (PayComponentRules.Standard s : PayComponentRules.STANDARD) {
                componentRepo.save(PayComponent.create(tid, s.code(), s.name(), s.role(), s.sarsCode(), true));
            }
            try {
                componentRepo.flush();
            } catch (DataIntegrityViolationException e) {
                // two first requests at once: the other one made them
                log.debug("Standard pay components already made for tenant {}", tid);
            }
            all = componentRepo.findByTenant(tid);
        }
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional
    public PayComponentResponse create(TenantId tenantId, CreatePayComponentRequest req) {
        String role = req.role() == null ? null : req.role().trim().toUpperCase();
        String problem = PayComponentRules.componentProblem(req.code(), req.name(), role, req.sarsCode());
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PAY_COMPONENT");
        if (componentRepo.existsByCode(tenantId.getValue(), req.code().trim())) {
            throw new HandyFlowException("A component with that code already exists.", HttpStatus.CONFLICT, "PAY_COMPONENT_EXISTS");
        }
        PayComponent c = PayComponent.create(tenantId.getValue(), req.code(), req.name(), role, req.sarsCode(), false);
        try {
            componentRepo.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new HandyFlowException("A component with that code already exists.", HttpStatus.CONFLICT, "PAY_COMPONENT_EXISTS");
        }
        return toResponse(c);
    }

    /** An archived component cannot be given to anyone new; payslips already made keep their copy of it. */
    @Transactional
    public PayComponentResponse archive(TenantId tenantId, UUID componentId) {
        PayComponent c = component(tenantId, componentId);
        c.archive();
        componentRepo.save(c);
        return toResponse(c);
    }

    // ── per employee ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmployeeComponentResponse> forEmployee(TenantId tenantId, UUID clientId, UUID employeeId) {
        requireEmployee(tenantId, clientId, employeeId);
        List<PayEmployeeComponent> rows = employeeComponentRepo.findByEmployee(tenantId.getValue(), employeeId);
        Map<UUID, PayComponent> byId = components(tenantId, rows.stream().map(PayEmployeeComponent::getComponentId).toList());
        return rows.stream().map(r -> employeeResponse(r, byId.get(r.getComponentId())))
                .sorted(Comparator.comparing(EmployeeComponentResponse::name)).toList();
    }

    /** Set what an employee gets (or has taken) every pay period. An amount of zero or none removes it. */
    @Transactional
    public List<EmployeeComponentResponse> setForEmployee(TenantId tenantId, UUID clientId, UUID employeeId, SetEmployeeComponentRequest req) {
        requireEmployee(tenantId, clientId, employeeId);
        PayComponent c = component(tenantId, req.componentId());
        Optional<PayEmployeeComponent> existing = employeeComponentRepo.findByEmployee(tenantId.getValue(), employeeId).stream()
                .filter(r -> r.getComponentId().equals(c.getId())).findFirst();
        if (req.amount() == null || req.amount().signum() == 0) {
            existing.ifPresent(employeeComponentRepo::delete);
        } else {
            requireUsable(c);
            requireAmount(req.amount());
            if (existing.isPresent()) {
                existing.get().setAmount(req.amount());
                employeeComponentRepo.save(existing.get());
            } else {
                employeeComponentRepo.save(PayEmployeeComponent.create(tenantId.getValue(), employeeId, c.getId(), req.amount()));
            }
        }
        log.info("Pay component set tenant={} employee={} component={} amount={}", tenantId.getValue(), employeeId, c.getCode(), req.amount());
        return forEmployee(tenantId, clientId, employeeId);
    }

    // ── per pay run ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RunItemResponse> itemsOfRun(TenantId tenantId, UUID runId) {
        loadRun(tenantId, runId);
        List<PayRunItem> items = runItemRepo.findByRun(tenantId.getValue(), runId);
        Map<UUID, PayComponent> byId = components(tenantId, items.stream().map(PayRunItem::getComponentId).toList());
        return items.stream().map(i -> itemResponse(i, byId.get(i.getComponentId()))).toList();
    }

    /** Add or change an item for an employee in a DRAFT pay run. An amount of zero or none removes it. */
    @Transactional
    public List<RunItemResponse> setItem(TenantId tenantId, UUID runId, SetRunItemRequest req) {
        PayRun run = loadRun(tenantId, runId);
        requireDraft(run);
        PayEmployee emp = employeeRepo.findActiveById(tenantId.getValue(), req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", req.employeeId().toString()));
        if (!emp.getPayClientId().equals(run.getPayClientId())) {
            throw new HandyFlowException("That employee does not belong to this pay run's client.", HttpStatus.BAD_REQUEST, "EMPLOYEE_NOT_IN_RUN");
        }
        PayComponent c = component(tenantId, req.componentId());
        Optional<PayRunItem> existing = runItemRepo.findOne(tenantId.getValue(), runId, emp.getId(), c.getId());
        if (req.amount() == null || req.amount().signum() == 0) {
            existing.ifPresent(runItemRepo::delete);
        } else {
            requireUsable(c);
            requireAmount(req.amount());
            String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
            if (note != null && note.length() > 200) {
                throw new HandyFlowException("A note is at most 200 characters.", HttpStatus.BAD_REQUEST, "INVALID_PAY_ITEM");
            }
            if (existing.isPresent()) {
                existing.get().change(req.amount(), note);
                runItemRepo.save(existing.get());
            } else {
                runItemRepo.save(PayRunItem.create(tenantId.getValue(), runId, emp.getId(), c.getId(), req.amount(), note));
            }
        }
        return itemsOfRun(tenantId, runId);
    }

    @Transactional
    public List<RunItemResponse> removeItem(TenantId tenantId, UUID runId, UUID itemId) {
        PayRun run = loadRun(tenantId, runId);
        requireDraft(run);
        PayRunItem item = runItemRepo.findByRunAndId(tenantId.getValue(), runId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRunItem", itemId.toString()));
        runItemRepo.delete(item);
        return itemsOfRun(tenantId, runId);
    }

    // ── used when a pay run is processed ───────────────────────────────────────

    /** A component line an employee's payslip will carry. */
    public record PayLine(PayComponent component, String source, java.math.BigDecimal amount, String note) {}

    /** Every line for every employee of a run: their recurring components and the run's own items. */
    @Transactional(readOnly = true)
    public Map<UUID, List<PayLine>> linesForRun(TenantId tenantId, PayRun run) {
        UUID tid = tenantId.getValue();
        List<PayEmployeeComponent> recurring = employeeComponentRepo.findByClient(tid, run.getPayClientId());
        List<PayRunItem> items = runItemRepo.findByRun(tid, run.getId());
        Set<UUID> ids = new HashSet<>();
        recurring.forEach(r -> ids.add(r.getComponentId()));
        items.forEach(i -> ids.add(i.getComponentId()));
        Map<UUID, PayComponent> byId = components(tenantId, ids);
        Map<UUID, List<PayLine>> out = new HashMap<>();
        for (PayEmployeeComponent r : recurring) {
            PayComponent c = byId.get(r.getComponentId());
            // an archived component stops being paid: it was retired on purpose
            if (c != null && c.isActive()) {
                out.computeIfAbsent(r.getPayEmployeeId(), k -> new ArrayList<>()).add(new PayLine(c, "RECURRING", r.getAmount(), null));
            }
        }
        for (PayRunItem i : items) {
            PayComponent c = byId.get(i.getComponentId());
            if (c == null) continue;
            out.computeIfAbsent(i.getPayEmployeeId(), k -> new ArrayList<>()).add(new PayLine(c, "RUN", i.getAmount(), i.getNote()));
        }
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private PayComponent component(TenantId tenantId, UUID id) {
        return componentRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("PayComponent", id.toString()));
    }

    private Map<UUID, PayComponent> components(TenantId tenantId, java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return componentRepo.findByTenantAndIds(tenantId.getValue(), new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(PayComponent::getId, Function.identity()));
    }

    private PayRun loadRun(TenantId tenantId, UUID runId) {
        return payRunRepo.findByTenantAndId(tenantId.getValue(), runId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", runId.toString()));
    }

    private void requireDraft(PayRun run) {
        if (!"DRAFT".equals(run.getStatus())) {
            throw new HandyFlowException("Items can only be changed while the pay run is a draft.", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
    }

    private void requireEmployee(TenantId tenantId, UUID clientId, UUID employeeId) {
        PayEmployee e = employeeRepo.findActiveById(tenantId.getValue(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("PayEmployee", employeeId.toString()));
        if (!e.getPayClientId().equals(clientId)) throw new ResourceNotFoundException("PayEmployee", employeeId.toString());
    }

    private void requireUsable(PayComponent c) {
        if (!c.isActive()) {
            throw new HandyFlowException("That component is archived and cannot be given to anyone.", HttpStatus.BAD_REQUEST, "PAY_COMPONENT_ARCHIVED");
        }
    }

    private void requireAmount(java.math.BigDecimal amount) {
        String problem = PayComponentRules.amountProblem(amount);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PAY_AMOUNT");
    }

    private PayComponentResponse toResponse(PayComponent c) {
        return new PayComponentResponse(c.getId(), c.getCode(), c.getName(), c.getRole(), c.getSarsCode(), c.isStandard(), c.getStatus());
    }

    private EmployeeComponentResponse employeeResponse(PayEmployeeComponent r, PayComponent c) {
        return new EmployeeComponentResponse(r.getId(), r.getComponentId(), c == null ? "?" : c.getCode(),
                c == null ? "Unknown" : c.getName(), c == null ? "" : c.getRole(), r.getAmount());
    }

    private RunItemResponse itemResponse(PayRunItem i, PayComponent c) {
        return new RunItemResponse(i.getId(), i.getPayEmployeeId(), i.getComponentId(), c == null ? "?" : c.getCode(),
                c == null ? "Unknown" : c.getName(), c == null ? "" : c.getRole(), i.getAmount(), i.getNote());
    }
}
