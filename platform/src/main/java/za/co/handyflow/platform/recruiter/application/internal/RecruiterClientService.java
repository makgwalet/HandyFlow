package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterClientRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterClientDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** The companies a recruiter hires for, who to talk to at each, which jobs are theirs, and the placements those jobs produced. Every query is filtered to the company. */
@Service
@RequiredArgsConstructor
public class RecruiterClientService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ClientRow> clients(TenantId tenantId, String q, String status) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
                SELECT c.id, c.name, c.industry, c.location, c.status,
                       (SELECT k.name FROM rec_client_contacts k WHERE k.client_id = c.id ORDER BY k.is_primary DESC, k.created_at LIMIT 1) AS contact,
                       (SELECT COUNT(*) FROM rec_jobs j WHERE j.client_id = c.id AND j.deleted_at IS NULL AND j.status = 'OPEN') AS open_jobs,
                       (SELECT COUNT(*) FROM rec_jobs j WHERE j.client_id = c.id AND j.deleted_at IS NULL) AS total_jobs,
                       (SELECT COUNT(*) FROM rec_applications a JOIN rec_jobs j ON j.id = a.job_id WHERE j.client_id = c.id AND a.stage = 'HIRED') AS placements
                FROM rec_clients c
                WHERE c.tenant_id = ? AND c.deleted_at IS NULL
                  AND (?::text IS NULL OR c.status = ?)
                  AND (?::text IS NULL OR lower(c.name) LIKE ? ESCAPE '\\' OR lower(COALESCE(c.industry, '')) LIKE ? ESCAPE '\\' OR lower(COALESCE(c.location, '')) LIKE ? ESCAPE '\\')
                ORDER BY lower(c.name) LIMIT 500
                """, (rs, i) -> new ClientRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("industry"), rs.getString("location"),
                rs.getString("status"), rs.getString("contact"), rs.getLong("open_jobs"), rs.getLong("total_jobs"), rs.getLong("placements")),
                tenantId.getValue(), status, status, like, like, like, like);
    }

    @Transactional(readOnly = true)
    public ClientDetail detail(TenantId tenantId, UUID id) {
        UUID t = tenantId.getValue();
        List<ClientDetail> found = jdbc.query("SELECT id, name, industry, location, status, notes, created_at FROM rec_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                (rs, i) -> new ClientDetail(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("industry"), rs.getString("location"), rs.getString("status"),
                        rs.getString("notes"), rs.getTimestamp("created_at").toInstant(), List.of(), List.of()), id, t);
        if (found.isEmpty()) throw new ResourceNotFoundException("Client", id.toString());
        ClientDetail c = found.get(0);
        List<Contact> contacts = jdbc.query("SELECT id, name, email, phone, role_title, is_primary FROM rec_client_contacts WHERE client_id = ? AND tenant_id = ? ORDER BY is_primary DESC, created_at",
                (rs, i) -> new Contact(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("role_title"), rs.getBoolean("is_primary")), id, t);
        List<ClientJob> jobs = jdbc.query("""
                SELECT j.id, j.title, j.status, COUNT(a.id) AS apps, COUNT(a.id) FILTER (WHERE a.stage = 'HIRED') AS hires
                FROM rec_jobs j LEFT JOIN rec_applications a ON a.job_id = j.id
                WHERE j.client_id = ? AND j.tenant_id = ? AND j.deleted_at IS NULL GROUP BY j.id, j.title, j.status, j.created_at ORDER BY j.created_at DESC
                """, (rs, i) -> new ClientJob(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status"), rs.getLong("apps"), rs.getLong("hires")), id, t);
        return new ClientDetail(c.id(), c.name(), c.industry(), c.location(), c.status(), c.notes(), c.createdAt(), contacts, jobs);
    }

    @Transactional
    public ClientDetail create(TenantId tenantId, ClientRequest r, UUID userId) {
        problem(RecruiterClientRules.clientProblem(r.name(), r.industry(), r.location(), r.notes()));
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO rec_clients (id, tenant_id, name, industry, location, notes, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId.getValue(), r.name().trim(), RecruiterClientRules.clean(r.industry()), RecruiterClientRules.clean(r.location()), RecruiterClientRules.clean(r.notes()), userId);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("You already have a client with this name.", e);
        }
        return detail(tenantId, id);
    }

    @Transactional
    public ClientDetail update(TenantId tenantId, UUID id, ClientRequest r) {
        problem(RecruiterClientRules.clientProblem(r.name(), r.industry(), r.location(), r.notes()));
        problem(RecruiterClientRules.statusProblem(r.status()));
        try {
            int n = jdbc.update("UPDATE rec_clients SET name = ?, industry = ?, location = ?, notes = ?, status = COALESCE(?, status), updated_at = NOW() WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                    r.name().trim(), RecruiterClientRules.clean(r.industry()), RecruiterClientRules.clean(r.location()), RecruiterClientRules.clean(r.notes()), r.status(), id, tenantId.getValue());
            if (n == 0) throw new ResourceNotFoundException("Client", id.toString());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("You already have a client with this name.", e);
        }
        return detail(tenantId, id);
    }

    /** The client goes; its jobs stay, no longer tied to anyone. */
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        requireClient(tenantId, id);
        jdbc.update("UPDATE rec_jobs SET client_id = NULL WHERE client_id = ? AND tenant_id = ?", id, tenantId.getValue());
        jdbc.update("DELETE FROM rec_client_contacts WHERE client_id = ? AND tenant_id = ?", id, tenantId.getValue());
        jdbc.update("UPDATE rec_clients SET deleted_at = NOW(), updated_at = NOW() WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
    }

    @Transactional
    public ClientDetail addContact(TenantId tenantId, UUID clientId, ContactRequest r) {
        problem(RecruiterClientRules.contactProblem(r.name(), r.email(), r.phone(), r.roleTitle()));
        requireClient(tenantId, clientId);
        boolean first = jdbc.queryForObject("SELECT COUNT(*) FROM rec_client_contacts WHERE client_id = ? AND tenant_id = ?", Long.class, clientId, tenantId.getValue()) == 0;
        boolean primary = first || Boolean.TRUE.equals(r.primary());
        if (primary) jdbc.update("UPDATE rec_client_contacts SET is_primary = FALSE WHERE client_id = ? AND tenant_id = ?", clientId, tenantId.getValue());
        jdbc.update("INSERT INTO rec_client_contacts (id, tenant_id, client_id, name, email, phone, role_title, is_primary) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId.getValue(), clientId, r.name().trim(), RecruiterClientRules.clean(r.email()), RecruiterClientRules.clean(r.phone()),
                RecruiterClientRules.clean(r.roleTitle()), primary);
        return detail(tenantId, clientId);
    }

    @Transactional
    public ClientDetail removeContact(TenantId tenantId, UUID clientId, UUID contactId) {
        requireClient(tenantId, clientId);
        int n = jdbc.update("DELETE FROM rec_client_contacts WHERE id = ? AND client_id = ? AND tenant_id = ?", contactId, clientId, tenantId.getValue());
        if (n == 0) throw new ResourceNotFoundException("Contact", contactId.toString());
        // Someone must still be the main contact if anyone is left.
        jdbc.update("""
                UPDATE rec_client_contacts SET is_primary = TRUE WHERE id = (SELECT id FROM rec_client_contacts WHERE client_id = ? AND tenant_id = ? ORDER BY created_at LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM rec_client_contacts WHERE client_id = ? AND tenant_id = ? AND is_primary)
                """, clientId, tenantId.getValue(), clientId, tenantId.getValue());
        return detail(tenantId, clientId);
    }

    /** Ties a job to a client, or frees it when clientId is null. */
    @Transactional
    public void setJobClient(TenantId tenantId, UUID jobId, UUID clientId) {
        if (clientId != null) requireClient(tenantId, clientId);
        int n = jdbc.update("UPDATE rec_jobs SET client_id = ? WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", clientId, jobId, tenantId.getValue());
        if (n == 0) throw new ResourceNotFoundException("Job", jobId.toString());
    }

    @Transactional(readOnly = true)
    public List<JobClient> jobClients(TenantId tenantId) {
        return jdbc.query("SELECT j.id, c.id AS client_id, c.name FROM rec_jobs j JOIN rec_clients c ON c.id = j.client_id AND c.deleted_at IS NULL WHERE j.tenant_id = ? AND j.deleted_at IS NULL",
                (rs, i) -> new JobClient(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class), rs.getString("name")), tenantId.getValue());
    }

    /** Hired candidates on a client's jobs, hired in the last {@code days} days. */
    @Transactional(readOnly = true)
    public Placements placements(TenantId tenantId, int days, Instant now) {
        Timestamp from = Timestamp.from(now.minus(days, ChronoUnit.DAYS));
        List<Placement> rows = jdbc.query("""
                SELECT a.id, ap.first_name || ' ' || ap.last_name AS candidate, j.id AS job_id, j.title, c.id AS client_id, c.name AS client_name,
                       a.hired_at, a.offered_start_date, a.placement_fee, (a.hr_employee_id IS NOT NULL) AS on_hr
                FROM rec_applications a JOIN rec_jobs j ON j.id = a.job_id JOIN rec_clients c ON c.id = j.client_id AND c.deleted_at IS NULL
                JOIN rec_applicants ap ON ap.id = a.applicant_id
                WHERE a.tenant_id = ? AND a.stage = 'HIRED' AND a.hired_at >= ?
                ORDER BY a.hired_at DESC LIMIT 500
                """, (rs, i) -> new Placement(rs.getObject("id", UUID.class), rs.getString("candidate"), rs.getObject("job_id", UUID.class), rs.getString("title"),
                rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getTimestamp("hired_at").toInstant(),
                rs.getDate("offered_start_date") == null ? null : rs.getDate("offered_start_date").toLocalDate(), rs.getBigDecimal("placement_fee"), rs.getBoolean("on_hr")),
                tenantId.getValue(), from);
        BigDecimal total = rows.stream().map(Placement::fee).filter(f -> f != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        long withoutFee = rows.stream().filter(p -> p.fee() == null).count();
        return new Placements(days, rows.size(), total, withoutFee, rows);
    }

    @Transactional
    public void setFee(TenantId tenantId, UUID applicationId, BigDecimal fee) {
        problem(RecruiterClientRules.feeProblem(fee));
        int n = jdbc.update("""
                UPDATE rec_applications a SET placement_fee = ? WHERE a.id = ? AND a.tenant_id = ? AND a.stage = 'HIRED'
                  AND EXISTS (SELECT 1 FROM rec_jobs j WHERE j.id = a.job_id AND j.client_id IS NOT NULL)
                """, fee, applicationId, tenantId.getValue());
        if (n == 0) throw new HandyFlowException("A fee can only be recorded for a hired candidate on a client's job.", HttpStatus.BAD_REQUEST, "NOT_A_PLACEMENT");
    }

    private void requireClient(TenantId tenantId, UUID id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM rec_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Long.class, id, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("Client", id.toString());
    }

    private static void problem(String p) {
        if (p != null) throw new HandyFlowException(p, HttpStatus.BAD_REQUEST, "INVALID_CLIENT");
    }
}
