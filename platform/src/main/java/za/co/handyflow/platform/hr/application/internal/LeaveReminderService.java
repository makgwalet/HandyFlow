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
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.Expiring;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells employees, before the year ends, that annual leave above the company's carry-over limit will be forfeited at the rollover, tells each
 * manager which of their team could lose leave, and tells the company's administrators how many people were told. Also tells employees and
 * their managers, 30, 14, 7 and 1 days ahead, when carried-over leave will expire. Only companies with a limit that applies to the coming rollover are looked at. Which days
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

    /**
     * Sends today's reminders for every company. A limit on carrying leave is reminded on its own days (see {@link LeaveReminderRules#reminderToday});
     * carried-over leave that will expire is reminded 30, 14, 7 and 1 days before the last day it can be used. Returns how many messages went out.
     */
    public int remindAll(LocalDate today) {
        boolean capDay = LeaveReminderRules.reminderToday(today);
        int sent = 0;
        for (HrLeavePolicy policy : policyRepo.findAll()) {
            boolean cap = capDay && LeavePolicyRules.capFor(policy.getCarryOverCapDays(), policy.getEffectiveYear(), today.getYear() + 1) != null;
            Integer months = LeavePolicyRules.expiryFor(policy.getCarryExpiryMonths(), policy.getEffectiveYear(), today.getYear());
            boolean expiry = months != null && LeaveReminderRules.expiryReminderToday(LeavePolicyRules.expiryDate(today.getYear(), months), today);
            if (!cap && !expiry) continue;
            try {
                sent += remindTenant(TenantId.of(policy.getTenantId()), today, cap, expiry);
            } catch (RuntimeException e) {
                log.error("Leave reminders failed for company {}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.info("Leave reminders: {} message(s)", sent);
        return sent;
    }

    private int remindTenant(TenantId tenant, LocalDate today, boolean cap, boolean expiry) {
        AtRiskReport report = leaveService.atRisk(tenant);
        int told = 0;
        if (cap && report.carryOverCapDays() != null && !report.employees().isEmpty()) told += remindCap(tenant, report, today);
        if (expiry && report.expiringOn() != null && report.expiring() != null && !report.expiring().isEmpty()) told += remindExpiry(tenant, report, today);
        return told;
    }

    private int remindCap(TenantId tenant, AtRiskReport report, LocalDate today) {
        int told = 0;
        Map<UUID, List<LeaveReminderRules.Line>> byManager = new LinkedHashMap<>();
        for (AtRisk a : report.employees()) {
            HrEmployee e = employeeRepo.findActiveById(tenant, a.employeeId()).filter(x -> !"TERMINATED".equals(x.getStatus())).orElse(null);
            if (e != null && e.getManagerId() != null) byManager.computeIfAbsent(e.getManagerId(), k -> new ArrayList<>()).add(new LeaveReminderRules.Line(e.getFullName(), a.wouldLose()));
            Recipient to = PerfNotifier.contactOf(e);
            if (to == null) continue;
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.employeeTitle(a.wouldLose()),
                    LeaveReminderRules.employeeMessage(a.wouldLose(), report.carryOverCapDays(), a.availableNow(), today.getYear()),
                    a.employeeId(), "/hr/leave", to);
            told++;
        }
        for (Map.Entry<UUID, List<LeaveReminderRules.Line>> m : byManager.entrySet()) {
            Recipient to = managerContact(tenant, m.getKey());
            if (to == null) continue;
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.teamTitle(m.getValue().size(), false),
                    LeaveReminderRules.teamCapMessage(m.getValue(), report.carryOverCapDays(), today.getYear()), m.getKey(), "/hr/leave", to);
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

    /** Each person with unused carried-over days is told, and so is their manager (one message listing the team). */
    private int remindExpiry(TenantId tenant, AtRiskReport report, LocalDate today) {
        int told = 0;
        Map<UUID, List<LeaveReminderRules.Line>> byManager = new LinkedHashMap<>();
        for (Expiring x : report.expiring()) {
            HrEmployee e = employeeRepo.findActiveById(tenant, x.employeeId()).filter(y -> !"TERMINATED".equals(y.getStatus())).orElse(null);
            if (e != null && e.getManagerId() != null) byManager.computeIfAbsent(e.getManagerId(), k -> new ArrayList<>()).add(new LeaveReminderRules.Line(e.getFullName(), x.unused()));
            Recipient to = PerfNotifier.contactOf(e);
            if (to == null) continue;
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.expiryEmployeeTitle(x.unused()),
                    LeaveReminderRules.expiryEmployeeMessage(x.unused(), report.expiringOn(), today, today.getYear()), x.employeeId(), "/hr/leave", to);
            told++;
        }
        for (Map.Entry<UUID, List<LeaveReminderRules.Line>> m : byManager.entrySet()) {
            Recipient to = managerContact(tenant, m.getKey());
            if (to == null) continue;
            notifier.send(tenant, NotificationType.LEAVE_AT_RISK, LeaveReminderRules.teamTitle(m.getValue().size(), true),
                    LeaveReminderRules.teamExpiryMessage(m.getValue(), report.expiringOn()), m.getKey(), "/hr/leave", to);
            told++;
        }
        return told;
    }

    private Recipient managerContact(TenantId tenant, UUID managerId) {
        return employeeRepo.findActiveById(tenant, managerId).filter(x -> !"TERMINATED".equals(x.getStatus())).map(PerfNotifier::contactOf).orElse(null);
    }
}
