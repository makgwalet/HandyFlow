package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.*;
import za.co.handyflow.platform.recruiter.dto.RecruiterPoolDtos.*;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Talent pools, tags and bringing a past candidate into a new vacancy. The rules are in {@link RecruiterPoolRules}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterPoolService {

    private final JdbcTemplate jdbc;
    private final RecApplicantRepository applicantRepo;
    private final RecApplicationRepository applicationRepo;
    private final RecJobRepository jobRepo;
    private final RecStageHistoryRepository historyRepo;
    private final RecruiterApprovalService approvalService;

    private static HandyFlowException bad(String message, String code) { return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code); }

    // ── Pools ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PoolView> pools(TenantId tenantId) {
        return jdbc.query("SELECT p.id, p.name, p.description, "
                        + "(SELECT COUNT(*) FROM rec_pool_members m JOIN rec_applicants a ON a.id = m.applicant_id WHERE m.pool_id = p.id AND a.anonymised_at IS NULL) AS members "
                        + "FROM rec_talent_pools p WHERE p.tenant_id = ? ORDER BY LOWER(p.name)",
                (rs, i) -> new PoolView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"), rs.getInt("members")),
                tenantId.getValue());
    }

    @Transactional
    public PoolView createPool(TenantId tenantId, PoolRequest req, UUID by) {
        String problem = RecruiterPoolRules.poolProblem(req == null ? null : req.name(), req == null ? null : req.description());
        if (problem != null) throw bad(problem, "INVALID_POOL");
        Integer same = jdbc.queryForObject("SELECT COUNT(*) FROM rec_talent_pools WHERE tenant_id = ? AND LOWER(name) = LOWER(?)", Integer.class,
                tenantId.getValue(), req.name().trim());
        if (same != null && same > 0) throw bad("A pool with that name already exists.", "DUPLICATE_POOL");
        UUID id = UUID.randomUUID();
        String description = req.description() == null || req.description().isBlank() ? null : req.description().trim();
        jdbc.update("INSERT INTO rec_talent_pools (id, tenant_id, name, description, created_by) VALUES (?,?,?,?,?)", id, tenantId.getValue(), req.name().trim(), description, by);
        return new PoolView(id, req.name().trim(), description, 0);
    }

    @Transactional
    public void deletePool(TenantId tenantId, UUID poolId) {
        requirePool(tenantId, poolId);
        jdbc.update("DELETE FROM rec_talent_pools WHERE id = ? AND tenant_id = ?", poolId, tenantId.getValue());
    }

    private void requirePool(TenantId tenantId, UUID poolId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rec_talent_pools WHERE id = ? AND tenant_id = ?", Integer.class, poolId, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("Talent pool", poolId.toString());
    }

    private RecApplicant requireApplicant(TenantId tenantId, UUID applicantId) {
        return applicantRepo.findById(applicantId).filter(a -> tenantId.equals(a.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", applicantId.toString()));
    }

    // ── Finding and reusing candidates ──────────────────────────────────────────

    /** Candidates of this company by name, email, location or tag, optionally only those in one pool. Newest activity first. */
    @Transactional(readOnly = true)
    public List<CandidateView> candidates(TenantId tenantId, String q, String tag, UUID poolId) {
        String qProblem = RecruiterSearchRules.problem(q);
        if (qProblem != null) throw bad(qProblem, "INVALID_SEARCH");
        if (poolId != null) requirePool(tenantId, poolId);
        String pattern = RecruiterSearchRules.active(q) ? RecruiterSearchRules.pattern(q) : null;
        String tagNorm = RecruiterPoolRules.normaliseTag(tag);
        return jdbc.query("""
                SELECT a.id, a.first_name, a.last_name, a.email, a.phone, a.location,
                       (SELECT COUNT(*) FROM rec_applications x WHERE x.applicant_id = a.id) AS applications,
                       GREATEST(a.updated_at, (SELECT MAX(GREATEST(x.updated_at, x.stage_changed_at)) FROM rec_applications x WHERE x.applicant_id = a.id)) AS last_activity,
                       m.note, m.added_at
                FROM rec_applicants a
                LEFT JOIN rec_pool_members m ON m.applicant_id = a.id AND m.pool_id = ?
                WHERE a.tenant_id = ? AND a.anonymised_at IS NULL
                  AND (?::uuid IS NULL OR m.pool_id IS NOT NULL)
                  AND (?::text IS NULL OR LOWER(a.first_name || ' ' || a.last_name) LIKE ? ESCAPE '\\'
                       OR LOWER(a.email) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(a.location, '')) LIKE ? ESCAPE '\\')
                  AND (?::text IS NULL OR EXISTS (SELECT 1 FROM rec_applicant_tags t WHERE t.applicant_id = a.id AND t.tag = ?))
                ORDER BY last_activity DESC NULLS LAST
                LIMIT 100
                """,
                (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Timestamp last = rs.getTimestamp("last_activity");
                    Timestamp added = rs.getTimestamp("added_at");
                    return new CandidateView(id, rs.getString("first_name") + " " + rs.getString("last_name"), rs.getString("email"), rs.getString("phone"),
                            rs.getString("location"), tagsOf(id), rs.getInt("applications"), last == null ? null : last.toInstant(),
                            rs.getString("note"), added == null ? null : added.toInstant());
                },
                poolId, tenantId.getValue(), poolId, pattern, pattern, pattern, pattern, tagNorm, tagNorm);
    }

    private List<String> tagsOf(UUID applicantId) {
        return jdbc.queryForList("SELECT tag FROM rec_applicant_tags WHERE applicant_id = ? ORDER BY tag", String.class, applicantId);
    }

    @Transactional
    public List<String> setTags(TenantId tenantId, UUID applicantId, TagsRequest req) {
        requireApplicant(tenantId, applicantId);
        List<String> in = req == null ? List.of() : req.tags();
        String problem = RecruiterPoolRules.tagsProblem(in);
        if (problem != null) throw bad(problem, "INVALID_TAGS");
        jdbc.update("DELETE FROM rec_applicant_tags WHERE applicant_id = ?", applicantId);
        for (String t : RecruiterPoolRules.cleanTags(in))
            jdbc.update("INSERT INTO rec_applicant_tags (applicant_id, tenant_id, tag) VALUES (?,?,?)", applicantId, tenantId.getValue(), t);
        return tagsOf(applicantId);
    }

    @Transactional
    public void addToPool(TenantId tenantId, UUID poolId, AddMemberRequest req, UUID by) {
        requirePool(tenantId, poolId);
        if (req == null || req.applicantId() == null) throw bad("Choose a candidate.", "INVALID_POOL");
        String noteProblem = RecruiterPoolRules.noteProblem(req.note());
        if (noteProblem != null) throw bad(noteProblem, "INVALID_POOL");
        RecApplicant applicant = requireApplicant(tenantId, req.applicantId());
        String problem = reuseProblem(tenantId, applicant);
        if (problem != null) throw bad(problem, "CANNOT_REUSE");
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        jdbc.update("INSERT INTO rec_pool_members (pool_id, applicant_id, note, added_by) VALUES (?,?,?,?) "
                + "ON CONFLICT (pool_id, applicant_id) DO UPDATE SET note = EXCLUDED.note", poolId, applicant.getId(), note, by);
    }

    @Transactional
    public void removeFromPool(TenantId tenantId, UUID poolId, UUID applicantId) {
        requirePool(tenantId, poolId);
        jdbc.update("DELETE FROM rec_pool_members WHERE pool_id = ? AND applicant_id = ?", poolId, applicantId);
    }

    /** Puts a past candidate forward for another vacancy. They keep the agreement they gave when they first applied. */
    @Transactional
    public UUID applyToJob(TenantId tenantId, UUID applicantId, UUID jobId, UUID by, String byName) {
        RecApplicant applicant = requireApplicant(tenantId, applicantId);
        RecJob job = jobRepo.findByIdAndTenantId(jobId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
        if (!"OPEN".equals(job.getStatus())) throw bad("This position is not accepting applications.", "JOB_CLOSED");
        String problem = reuseProblem(tenantId, applicant);
        if (problem != null) throw bad(problem, "CANNOT_REUSE");
        if (applicationRepo.findByJobIdAndApplicantId(jobId, applicantId).isPresent())
            throw bad("This candidate has already applied for this position.", "DUPLICATE_APPLICATION");
        List<Object[]> consent = jdbc.query("SELECT consent_given_at, consent_statement_ver, consent_privacy_url FROM rec_applications "
                        + "WHERE applicant_id = ? AND consent_given_at IS NOT NULL ORDER BY consent_given_at DESC LIMIT 1",
                (rs, i) -> new Object[]{rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3)}, applicantId);
        RecApplication application = RecApplication.create(tenantId, jobId, applicantId, "TALENT_POOL", null);
        application.inheritConsent((Instant) consent.get(0)[0], (String) consent.get(0)[1], (String) consent.get(0)[2]);
        applicationRepo.save(application);
        job.incrementApplicationCount();
        jobRepo.save(job);
        historyRepo.save(RecStageHistory.create(application.getId(), null, "APPLIED", byName, "Put forward from the talent pool"));
        log.info("Candidate={} put forward for job={} by user={}", applicantId, jobId, by);
        return application.getId();
    }

    private String reuseProblem(TenantId tenantId, RecApplicant applicant) {
        Instant consent = jdbc.queryForObject("SELECT MAX(consent_given_at) FROM rec_applications WHERE applicant_id = ?",
                (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), applicant.getId());
        Instant lastApp = jdbc.queryForObject("SELECT MAX(GREATEST(updated_at, stage_changed_at)) FROM rec_applications WHERE applicant_id = ?",
                (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), applicant.getId());
        Instant last = applicant.getUpdatedAt();
        if (lastApp != null && (last == null || lastApp.isAfter(last))) last = lastApp;
        return RecruiterPoolRules.reuseProblem(applicant.isAnonymised(), consent, last,
                approvalService.settings(tenantId).getRetentionMonths(), Instant.now());
    }
}
