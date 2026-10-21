package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.TaskListResponse;
import za.co.handyflow.platform.crm.dto.TaskResponse;
import za.co.handyflow.platform.crm.dto.UpdateTaskRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The CRM task queue: every follow-up of the tenant in one list, with the customer's name. Creating, completing, reopening and
 * deleting still go through the per-customer follow-up endpoints; this adds the cross-customer list and in-place changes
 * (reassign, snooze, priority). Tasks of deleted customers are not shown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmTaskService {

    public static final int OPEN_LIMIT = 1000;
    public static final int DONE_LIMIT = 300;
    public static final int DONE_DAYS = 30;

    private final CustomerFollowUpRepository followUpRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public TaskListResponse list(TenantId tenantId, boolean done) {
        List<CustomerFollowUp> rows;
        boolean truncated;
        if (done) {
            rows = followUpRepository.findCompletedSince(tenantId,
                    Instant.now().minus(DONE_DAYS, ChronoUnit.DAYS), PageRequest.of(0, DONE_LIMIT + 1));
            truncated = rows.size() > DONE_LIMIT;
        } else {
            rows = followUpRepository.findOpen(tenantId, PageRequest.of(0, OPEN_LIMIT + 1));
            truncated = rows.size() > OPEN_LIMIT;
        }
        if (truncated) rows = rows.subList(0, done ? DONE_LIMIT : OPEN_LIMIT);
        Map<UUID, String> names = customerNames(tenantId, rows);
        LocalDate today = LocalDate.now();
        List<TaskResponse> tasks = rows.stream()
                .filter(f -> names.containsKey(f.getCustomerId()))   // customer deleted or not in this tenant
                .map(f -> toResponse(f, names.get(f.getCustomerId()), today))
                .toList();
        return new TaskListResponse(tasks, truncated);
    }

    @Transactional
    public TaskResponse update(TenantId tenantId, UUID id, UpdateTaskRequest req) {
        var task = followUpRepository.findByIdAndTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FollowUp", id.toString()));
        if (task.isCompleted()) {
            throw new IllegalStateException("A finished task cannot be changed. Reopen it first.");
        }
        LocalDate today = LocalDate.now();
        if (req.dueDate() != null) {
            if (req.dueDate().isBefore(today)) {
                throw new IllegalArgumentException("The due date cannot be in the past");
            }
            task.moveDueDate(req.dueDate());
        }
        if (req.assignedTo() != null) task.reassign(req.assignedTo());
        if (req.priority() != null) task.changePriority(req.priority());
        followUpRepository.save(task);
        log.info("[CRM] Task updated id={} tenant={}", id, tenantId);
        Map<UUID, String> names = customerNames(tenantId, List.of(task));
        return toResponse(task, names.getOrDefault(task.getCustomerId(), ""), today);
    }

    private Map<UUID, String> customerNames(TenantId tenantId, List<CustomerFollowUp> rows) {
        Set<UUID> ids = rows.stream().map(CustomerFollowUp::getCustomerId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        for (Customer c : customerRepository.findAllById(ids)) {
            if (tenantId.equals(c.getTenantId()) && c.getDeletedAt() == null) names.put(c.getId(), c.getName());
        }
        return names;
    }

    private TaskResponse toResponse(CustomerFollowUp f, String customerName, LocalDate today) {
        boolean overdue = !f.isCompleted() && f.getDueDate().isBefore(today);
        return new TaskResponse(
                f.getId(), f.getCustomerId(), customerName, f.getDueDate(), f.getNote(), f.getAssignedTo(),
                f.getPriority().name(), f.isCompleted(), f.getCompletedAt(),
                f.getOutcome() != null ? f.getOutcome().name() : null,
                overdue, overdue ? ChronoUnit.DAYS.between(f.getDueDate(), today) : 0);
    }
}
