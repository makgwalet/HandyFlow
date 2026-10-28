package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrLeavePolicy;
import za.co.handyflow.platform.hr.domain.model.LeavePolicyRules;
import za.co.handyflow.platform.hr.domain.model.LeaveReminderRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrLeavePolicyRepository;
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.AtRisk;
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.AtRiskReport;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Tells employees, before the year ends, that annual leave above the company's carry-over limit will be forfeited at the rollover, and tells the
 * company's administrators how many people were told. Only companies with a limit that applies to the coming rollover are looked at. Which days
 * a reminder goes out is decided by {@link LeaveReminderRules}. Sending is best effort and never stops the job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveReminderService {

    private final HrLeavePolicyRepository policyRepo;
    private final LeaveService leaveService;
    private final HrEmployeeRepository employeeRepo;
    private final PerfNotifier notifier;
    private final TenantAdminRecipients adminRecipients;

    /** Sends today's reminders for every company that has a limit. Returns how many messages went out. */
    public int remindAll(LocalDate today) {
        if (!LeaveReminderRules.reminderToday(today)) return 0;
        int sent = 0;
        for (HrLeavePolicy policy : policyRepo.findAll()) {
            if (LeavePolicyRules.capFor(policy.getCarryOverCapDays(), policy.getEffectiveYear(), today.getYear() + 1) == null) continue;
            try {
                sent += remindTenant(TenantId.of(policy.getTenantId()), today);
            } catch (RuntimeException e) {
                log.error("Leave reminders failed for company {}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.info("Leave at-risk reminders: {} message(s)", sent);
        return sent;
    }

    private int remindTenant(TenantId tenant, LocalDate today) {
        AtRiskReport report = leaveService.atRisk(tenant);
        if (report.carryOverCapDays() == null || report.employees().isEmpty()) return 0;
        int told = 0;
        for (AtRisk a : report.employees()) {
            HrEmployee e = employeeRepo.findActiveById(tenant, a.employeeId()).filter(x -> !"TERMINATED".equals(x.getStatus())).orElse(null);
            Recipient to = PerfNotifier.contactOf(e);
            if (to == null) continue;
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.employeeTitle(a.wouldLose()),
                    LeaveReminderRules.employeeMessage(a.wouldLose(), report.carryOverCapDays(), a.availableNow(), today.getYear()),
                    a.employeeId(), "/hr/leave", to);
            told++;
        }
        List<Recipient> admins = adminRecipients.resolveTenantAdmins(tenant);
        if (!admins.isEmpty()) {
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.adminTitle(report.employees().size()),
                    LeaveReminderRules.adminMessage(report.employees().size(), report.carryOverCapDays(), today.getYear()),
                    tenant.getValue(), "/hr/leave", admins);
            told++;
        }
        return told;
    }
}
