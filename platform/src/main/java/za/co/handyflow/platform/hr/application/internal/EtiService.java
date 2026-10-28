package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.EtiRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The employment tax incentive table that applies on a date, and the two per-employee ETI facts (months used elsewhere, a reason they cannot qualify). */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtiService {

    private final JdbcTemplate jdbc;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;

    public record EmployeeEti(UUID employeeId, int priorMonths, String excludedReason) {}
    public record UpdateRequest(Integer priorMonths, String excludedReason) {}

    /** The table in force on the date, or empty when none applies (before the first row, or after it ended). */
    public Optional<EtiRules.Table> tableFor(LocalDate date) {
        List<EtiRules.Table> rows = jdbc.query("""
                SELECT full_month_min, remuneration_cap, max_first, max_second, taper_start, taper_rate_first, taper_rate_second
                  FROM hr_eti_rules WHERE effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
                 ORDER BY effective_from DESC LIMIT 1""",
                (rs, n) -> new EtiRules.Table(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7)), date, date);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Transactional(readOnly = true)
    public EmployeeEti get(TenantId tenantId, UUID employeeId) {
        HrEmployee e = find(tenantId, employeeId);
        return new EmployeeEti(e.getId(), e.getEtiPriorMonths(), e.getEtiExcludedReason());
    }

    @Transactional
    public EmployeeEti update(TenantId tenantId, UUID employeeId, UpdateRequest req, UUID actor) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this cannot be recorded.");
        int months = req == null || req.priorMonths() == null ? 0 : req.priorMonths();
        if (months < 0 || months > EtiRules.MAX_MONTHS)
            throw new IllegalArgumentException("Months of incentive already used must be between 0 and " + EtiRules.MAX_MONTHS + ".");
        String reason = req == null || req.excludedReason() == null || req.excludedReason().isBlank() ? null : req.excludedReason().trim();
        if (reason != null && reason.length() > 200) throw new IllegalArgumentException("Keep the reason under 200 characters.");
        HrEmployee e = find(tenantId, employeeId);
        e.setEti(months, reason);
        employeeRepo.save(e);
        audit.record(tenantId, e.getId(), "ETI_CHANGED", months + " months used" + (reason == null ? "" : ", cannot qualify: " + reason), actor);
        return new EmployeeEti(e.getId(), months, reason);
    }

    private HrEmployee find(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }
}
