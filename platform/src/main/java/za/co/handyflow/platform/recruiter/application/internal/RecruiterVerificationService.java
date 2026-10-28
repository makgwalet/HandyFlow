package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicationRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecStageHistoryRepository;
import za.co.handyflow.platform.recruiter.dto.RecruiterVerificationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Pre-hire checks on an application. The rules are in {@link RecruiterVerificationRules}; moving to Hired asks them (see RecruiterService). */
@Service
@RequiredArgsConstructor
public class RecruiterVerificationService {

    private final JdbcTemplate jdbc;
    private final RecApplicationRepository applicationRepo;
    private final RecStageHistoryRepository historyRepo;

    private RecApplication application(TenantId tenantId, UUID id) {
        return applicationRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }

    @Transactional(readOnly = true)
    public VerificationView view(TenantId tenantId, UUID applicationId) {
        application(tenantId, applicationId);
        return build(applicationId);
    }

    private VerificationView build(UUID applicationId) {
        List<CheckView> checks = jdbc.query("SELECT id, check_type, description, required, status, result_note, requested_at, decided_at "
                        + "FROM rec_verifications WHERE application_id = ? ORDER BY requested_at, id",
                (rs, i) -> {
                    Timestamp d = rs.getTimestamp("decided_at");
                    return new CheckView(rs.getObject("id", UUID.class), rs.getString("check_type"), rs.getString("description"), rs.getBoolean("required"),
                            rs.getString("status"), rs.getString("result_note"), rs.getTimestamp("requested_at").toInstant(), d == null ? null : d.toInstant());
                }, applicationId);
        String blocked = RecruiterVerificationRules.hireProblem(checks.stream()
                .map(c -> new RecruiterVerificationRules.Check(c.required(), c.status())).toList());
        int outstanding = (int) checks.stream().filter(c -> c.required() && RecruiterVerificationRules.PENDING.equals(c.status())).count();
        return new VerificationView(applicationId, checks, outstanding, blocked == null, blocked);
    }

    @Transactional
    public VerificationView add(TenantId tenantId, UUID applicationId, AddCheckRequest req, UUID by) {
        RecApplication app = application(tenantId, applicationId);
        String problem = RecruiterVerificationRules.addProblem(req == null ? null : req.type(), req == null ? null : req.description(), app.getStage());
        if (problem != null) throw bad(problem, "INVALID_CHECK");
        boolean required = req.required() == null || req.required();
        jdbc.update("INSERT INTO rec_verifications (id, tenant_id, application_id, check_type, description, required, requested_by) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId.getValue(), applicationId, req.type(), req.description().trim(), required, by);
        return build(applicationId);
    }

    /** {@code canApprove}: whether the person holds the approver permission, needed to waive a check. */
    @Transactional
    public VerificationView decide(TenantId tenantId, UUID applicationId, UUID checkId, DecisionRequest req, UUID by, boolean canApprove) {
        RecApplication app = application(tenantId, applicationId);
        List<Object[]> rows = jdbc.query("SELECT status, description, required FROM rec_verifications WHERE id = ? AND application_id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBoolean(3)}, checkId, applicationId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Check", checkId.toString());
        String current = (String) rows.get(0)[0], description = (String) rows.get(0)[1];
        String problem = RecruiterVerificationRules.decisionProblem(current, req == null ? null : req.status(), req == null ? null : req.note());
        if (problem != null) throw bad(problem, "INVALID_CHECK");
        String status = req.status().trim().toUpperCase();
        if (RecruiterVerificationRules.needsApprover(status) && !canApprove)
            throw new HandyFlowException("Only an approver can waive a check.", HttpStatus.FORBIDDEN, "APPROVER_REQUIRED");
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        jdbc.update("UPDATE rec_verifications SET status = ?, result_note = ?, decided_by = ?, decided_at = NOW() WHERE id = ?", status, note, by, checkId);
        historyRepo.save(RecStageHistory.create(applicationId, app.getStage(), app.getStage(), userName(by),
                "Check " + status.toLowerCase() + ": " + description + (note == null ? "" : " (" + note + ")")));
        return build(applicationId);
    }

    private String userName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject("SELECT first_name || ' ' || last_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) { return null; }
    }

    @Transactional
    public VerificationView remove(TenantId tenantId, UUID applicationId, UUID checkId) {
        application(tenantId, applicationId);
        List<String> status = jdbc.queryForList("SELECT status FROM rec_verifications WHERE id = ? AND application_id = ?", String.class, checkId, applicationId);
        if (status.isEmpty()) throw new ResourceNotFoundException("Check", checkId.toString());
        String problem = RecruiterVerificationRules.removeProblem(status.get(0));
        if (problem != null) throw bad(problem, "INVALID_CHECK");
        jdbc.update("DELETE FROM rec_verifications WHERE id = ?", checkId);
        return build(applicationId);
    }
}
