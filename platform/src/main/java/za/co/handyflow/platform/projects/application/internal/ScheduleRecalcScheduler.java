package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Nightly refresh of critical-path flags for every ACTIVE project, so "critical" stays
 * correct as the calendar moves even when nobody touches the schedule.
 *
 * Safe to run on several instances at once: recalculation is idempotent (it writes the same
 * values), so overlapping runs waste work but cannot corrupt anything. A distributed lock
 * (e.g. ShedLock) would remove the duplication; not added here to avoid a new dependency.
 *
 * Disable with app.projects.schedule.nightly-recalc=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRecalcScheduler {

    private final ScheduleService scheduleService;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.projects.schedule.nightly-recalc:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    public void recalculateActiveProjects() {
        if (!enabled) return;

        List<Object[]> projects = em.createQuery(
                        "SELECT p.id, p.tenantId FROM Project p WHERE p.status = :s", Object[].class)
                .setParameter("s", ProjectStatus.ACTIVE)
                .getResultList();

        int ok = 0, failed = 0;
        for (Object[] row : projects) {
            try {
                scheduleService.recalculate(TenantId.of((UUID) row[1]), (UUID) row[0]);
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Nightly schedule recalculation failed for project={}: {}", row[0], e.getMessage());
            }
        }
        log.info("Nightly schedule recalculation: {} project(s) ok, {} failed", ok, failed);
    }
}