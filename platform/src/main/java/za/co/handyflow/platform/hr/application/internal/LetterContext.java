package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrErChargeRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out the value of every merge field for one employee, and for one case when the letter belongs to one. A field with nothing to
 * show is left out, so the letter marks it as a gap for HR to fill in. Compensation, ID and tax numbers and bank details are never
 * available as merge fields.
 */
@Component
@RequiredArgsConstructor
class LetterContext {

    private final HrEmployeeRepository employeeRepo;
    private final HrErChargeRepository chargeRepo;
    private final ErPolicyService policyService;
    private final JdbcTemplate jdbc;

    String companyName(TenantId tenantId) {
        try {
            Object name = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue()).get("name");
            if (name != null && !name.toString().isBlank()) return name.toString();
        } catch (Exception ignored) {
            // fall through to the neutral name below
        }
        return "The Company";
    }

    Map<String, String> build(TenantId tenantId, HrEmployee emp, HrErCase c, LocalDate today) {
        Map<String, String> v = new HashMap<>();
        put(v, "company.name", companyName(tenantId));
        put(v, "today", LetterRules.longDate(today));
        put(v, "employee.name", emp.getFullName());
        put(v, "employee.firstName", emp.getFirstName());
        put(v, "employee.lastName", emp.getLastName());
        put(v, "employee.number", emp.getEmployeeNumber());
        put(v, "employee.jobTitle", emp.getJobTitle());
        put(v, "employee.department", emp.getDepartment());
        put(v, "employee.startDate", LetterRules.longDate(emp.getStartDate()));
        put(v, "employee.endDate", LetterRules.longDate(emp.getEndDate()));
        ErPolicy policy = policyService.get(tenantId);
        put(v, "hearing.noticeHours", String.valueOf(policy.noticeHours()));
        put(v, "appeal.days", String.valueOf(policy.appealDays()));
        if (c == null) return v;

        put(v, "case.number", c.getCaseNumber());
        put(v, "case.title", c.getTitle());
        put(v, "case.category", LetterRules.words(c.getCategory()));
        put(v, "case.incidentDate", LetterRules.longDate(c.getIncidentDate()));
        put(v, "case.description", c.getDescription());
        List<String> charges = chargeRepo.forCase(c.getId()).stream().map(HrErCharge::getCharge).toList();
        put(v, "charges", LetterRules.numbered(charges));

        put(v, "hearing.date", LetterRules.longDate(c.getHearingDate()));
        if (c.getHearingTime() != null) put(v, "hearing.time", c.getHearingTime().toString().substring(0, 5));
        put(v, "hearing.venue", c.getHearingVenue());
        put(v, "hearing.chair", nameOf(tenantId, c.getChairId()));
        put(v, "hearing.initiator", nameOf(tenantId, c.getInitiatorId()));

        put(v, "outcome", LetterRules.words(c.getOutcome()));
        put(v, "outcome.date", LetterRules.longDate(c.getDecidedOn()));
        put(v, "outcome.reasons", c.getDecisionNote());
        if (c.getWarningMonths() != null) put(v, "warning.months", String.valueOf(c.getWarningMonths()));
        put(v, "warning.validUntil", LetterRules.longDate(c.getWarningValidUntil()));

        // The appeal runs from the day the employee is told. Before that is recorded, the day the letter is issued is the best guide.
        LocalDate told = c.getCommunicatedOn() != null ? c.getCommunicatedOn() : today;
        put(v, "appeal.deadline", LetterRules.longDate(DisciplineRules.appealDeadline(told, policy)));
        put(v, "appeal.outcome", LetterRules.words(c.getAppealOutcome()));
        put(v, "appeal.reasons", c.getAppealNote());
        if ("DISMISSAL".equals(c.getOutcome()))
            put(v, "ccma.deadline", LetterRules.longDate(DisciplineRules.ccmaDeadline(c.getCommunicatedOn() != null ? c.getCommunicatedOn() : (c.getDecidedOn() != null ? c.getDecidedOn() : today))));

        put(v, "suspension.from", LetterRules.longDate(c.getSuspendedFrom()));
        put(v, "suspension.to", c.getSuspendedFrom() == null ? null : (c.getSuspendedTo() == null ? "further notice" : LetterRules.longDate(c.getSuspendedTo())));
        put(v, "suspension.reason", c.getSuspensionReason());
        return v;
    }

    private String nameOf(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        return employeeRepo.findActiveById(tenantId, employeeId).map(HrEmployee::getFullName).orElse(null);
    }

    private static void put(Map<String, String> m, String key, String value) {
        if (value != null && !value.isBlank()) m.put(key, value);
    }
}
