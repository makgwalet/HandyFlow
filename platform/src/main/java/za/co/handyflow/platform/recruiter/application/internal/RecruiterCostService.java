package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterCostRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterCostDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a job cost to fill. Every query is filtered to the company. */
@Service
@RequiredArgsConstructor
public class RecruiterCostService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public JobCosts costs(TenantId tenantId, UUID jobId) {
        requireJob(tenantId, jobId);
        List<Cost> rows = jdbc.query("SELECT id, category, amount, incurred_on, note FROM rec_job_costs WHERE job_id = ? AND tenant_id = ? ORDER BY incurred_on DESC, created_at DESC",
                (rs, i) -> new Cost(rs.getObject("id", UUID.class), rs.getString("category"), rs.getBigDecimal("amount"), rs.getDate("incurred_on").toLocalDate(), rs.getString("note")),
                jobId, tenantId.getValue());
        return new JobCosts(jobId, rows.stream().map(Cost::amount).reduce(BigDecimal.ZERO, BigDecimal::add), rows);
    }

    @Transactional
    public JobCosts add(TenantId tenantId, UUID jobId, CostRequest r, UUID by, LocalDate today) {
        String problem = RecruiterCostRules.costProblem(r.category(), r.amount(), r.incurredOn(), r.note(), today);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_COST");
        requireJob(tenantId, jobId);
        jdbc.update("INSERT INTO rec_job_costs (id, tenant_id, job_id, category, amount, incurred_on, note, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId.getValue(), jobId, r.category(), r.amount(), r.incurredOn(), r.note() == null || r.note().isBlank() ? null : r.note().trim(), by);
        return costs(tenantId, jobId);
    }

    @Transactional
    public JobCosts remove(TenantId tenantId, UUID jobId, UUID costId) {
        int n = jdbc.update("DELETE FROM rec_job_costs WHERE id = ? AND job_id = ? AND tenant_id = ?", costId, jobId, tenantId.getValue());
        if (n == 0) throw new ResourceNotFoundException("Cost", costId.toString());
        return costs(tenantId, jobId);
    }

    private void requireJob(TenantId tenantId, UUID jobId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM rec_jobs WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Long.class, jobId, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("Job", jobId.toString());
    }
}
