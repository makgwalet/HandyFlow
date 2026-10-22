package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.EventView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** What every performance service needs: who the signed-in person is in the reporting lines, finding a review they may see, names, the history. */
@Component
@RequiredArgsConstructor
class PerfSupport {

    private final HrEmployeeRepository employeeRepo;
    private final HrPerfCycleRepository cycleRepo;
    private final HrPerfReviewRepository reviewRepo;
    private final HrPerfEventRepository eventRepo;

    LocalDate today() { return LocalDate.now(DisciplineRules.ZONE); }

    Optional<HrEmployee> me(TenantId tenantId, UUID userId) {
        return userId == null ? Optional.empty() : employeeRepo.findByUserId(tenantId, userId);
    }

    HrEmployee requireMe(TenantId tenantId, UUID userId) {
        if (userId == null) throw new AccessDeniedException("Your user could not be identified.");
        return employeeRepo.findByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
    }

    /** A live employee (not removed). Used for existing records, which may be about someone who has since left. */
    HrEmployee employee(TenantId tenantId, UUID id) {
        if (id == null) throw new IllegalArgumentException("Choose the employee.");
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    /** An employee who still works here, for new work. */
    HrEmployee serving(TenantId tenantId, UUID id) {
        HrEmployee e = employee(tenantId, id);
        if ("TERMINATED".equals(e.getStatus())) throw new IllegalArgumentException(e.getFullName() + " no longer works here.");
        return e;
    }

    Map<UUID, String> names(Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> out = new HashMap<>();
        if (wanted.isEmpty()) return out;
        for (HrEmployee e : employeeRepo.findAllById(wanted)) out.put(e.getId(), e.getFullName());
        return out;
    }

    String userName(TenantId tenantId, UUID userId, Map<UUID, String> cache) {
        if (userId == null) return null;
        return cache.computeIfAbsent(userId, u -> employeeRepo.findByUserId(tenantId, u).map(HrEmployee::getFullName).orElse("HR"));
    }

    void event(TenantId tenantId, String kind, UUID ref, String action, String note, UUID by) {
        eventRepo.save(HrPerfEvent.of(tenantId.getValue(), kind, ref, action, note, by));
    }

    List<EventView> events(TenantId tenantId, String kind, UUID ref) {
        Map<UUID, String> users = new HashMap<>();
        return eventRepo.forRef(kind, ref).stream()
                .map(e -> new EventView(e.getAction(), e.getNote(), userName(tenantId, e.getDoneBy(), users), e.getDoneAt())).toList();
    }

    boolean isManagerOf(HrEmployee me, HrEmployee subject) {
        return me != null && subject.getManagerId() != null && subject.getManagerId().equals(me.getId());
    }

    /** A review with everything needed to decide what the person may see and do. */
    record ReviewCtx(HrPerfReview review, HrPerfCycle cycle, HrEmployee subject, HrEmployee me, PerfRules.Who actionWho, PerfRules.Who sightWho) {
        boolean isSubject() { return actionWho.subject(); }
    }

    /** A review the person may see. One they may not see looks the same as one that does not exist. */
    ReviewCtx review(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrPerfReview r = reviewRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Review", String.valueOf(id)));
        HrEmployee me = me(tenantId, userId).orElse(null);
        PerfRules.Who sight = who(me, r, PerfRules.canRead(held));
        if (!sight.any()) throw new ResourceNotFoundException("Review", String.valueOf(id));
        HrPerfCycle cycle = cycleRepo.findByTenantAndId(tenantId, r.getCycleId()).orElseThrow(() -> new ResourceNotFoundException("Cycle", String.valueOf(r.getCycleId())));
        return new ReviewCtx(r, cycle, employee(tenantId, r.getEmployeeId()), me, who(me, r, PerfRules.canManage(held)), sight);
    }

    private PerfRules.Who who(HrEmployee me, HrPerfReview r, boolean hr) {
        boolean subject = me != null && me.getId().equals(r.getEmployeeId());
        boolean reviewer = me != null && r.getReviewerId() != null && me.getId().equals(r.getReviewerId());
        return PerfRules.Who.of(subject, reviewer, hr);
    }

    static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }

    static void requireRead(Set<String> held) {
        if (!PerfRules.canRead(held)) throw new AccessDeniedException("You do not have permission to see performance records.");
    }

    static void requireManage(Set<String> held) {
        if (!PerfRules.canManage(held)) throw new AccessDeniedException("You do not have permission to run performance reviews.");
    }

    /** Throws the right kind of refusal when an action is not on the list: not allowed for this person, or not possible in this state. */
    static void require(List<String> actions, String action, boolean roleOk, String denied, String state) {
        if (actions.contains(action)) return;
        if (!roleOk) throw new AccessDeniedException(denied);
        throw new IllegalStateException(state);
    }
}
