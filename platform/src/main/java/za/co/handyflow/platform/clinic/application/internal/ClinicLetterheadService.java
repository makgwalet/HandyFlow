package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.LetterheadRules.Row;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Letterhead;
import za.co.handyflow.platform.clinic.dto.LetterheadDtos.LetterheadRequest;
import za.co.handyflow.platform.clinic.dto.LetterheadDtos.LetterheadResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Practice and branch letterheads (patch 0193, CLINIC-DEC-026). Practice-wide is the default; a branch letterhead is used when the document names that branch. */
@Service
public class ClinicLetterheadService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ClinicLetterheadService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    /** A letterhead found for a document, with the id kept so the issued record can say which one was used. */
    public record Resolved(UUID id, Letterhead letterhead) {}

    @Transactional(readOnly = true)
    public List<LetterheadResponse> list(UUID tenantId) {
        return rows(tenantId, false).stream().map(this::response).toList();
    }

    @Transactional
    public LetterheadResponse create(UUID tenantId, UUID by, LetterheadRequest r) {
        List<String> lines = LetterheadRules.tidy(r == null ? null : r.lines());
        check(r, lines);
        UUID id = UUID.randomUUID();
        boolean branch = r.branchName() != null && !r.branchName().isBlank();
        boolean noDefaultYet = !branch && rows(tenantId, true).stream().noneMatch(Row::isDefault);
        boolean wantDefault = !branch && (Boolean.TRUE.equals(r.makeDefault()) || noDefaultYet);
        if (wantDefault) clearDefault(tenantId);
        try {
            jdbc.update("""
                    INSERT INTO clinic_letterheads (id, tenant_id, name, branch_name, practice_name, lines, is_default, created_by, updated_by)
                    VALUES (?,?,?,?,?,?::jsonb,?,?,?)""",
                    id, tenantId, r.name().trim(), blankToNull(r.branchName()), r.practiceName().trim(), toJson(lines), wantDefault, by, by);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("There is already a letterhead for that branch");
        }
        return response(find(tenantId, id));
    }

    @Transactional
    public LetterheadResponse update(UUID tenantId, UUID id, UUID by, LetterheadRequest r) {
        Row existing = find(tenantId, id);
        List<String> lines = LetterheadRules.tidy(r == null ? null : r.lines());
        check(r, lines);
        boolean branch = r.branchName() != null && !r.branchName().isBlank();
        boolean wantDefault = !branch && (Boolean.TRUE.equals(r.makeDefault()) || (existing.isDefault() && r.makeDefault() == null));
        if (wantDefault && !existing.isDefault()) clearDefault(tenantId);
        try {
            jdbc.update("""
                    UPDATE clinic_letterheads SET name=?, branch_name=?, practice_name=?, lines=?::jsonb, is_default=?, updated_at=now(), updated_by=?
                    WHERE tenant_id=? AND id=?""",
                    r.name().trim(), blankToNull(r.branchName()), r.practiceName().trim(), toJson(lines), wantDefault, by, tenantId, id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("There is already a letterhead for that branch");
        }
        return response(find(tenantId, id));
    }

    /** Takes it out of use. Documents already issued keep what was printed on them. The default cannot be removed while others exist without choosing a new default. */
    @Transactional
    public void deactivate(UUID tenantId, UUID id, UUID by) {
        Row r = find(tenantId, id);
        if (r.isDefault() && rows(tenantId, true).stream().anyMatch(x -> !x.id().equals(id) && x.branchName() == null))
            throw new IllegalArgumentException("Make another practice-wide letterhead the default before removing this one");
        jdbc.update("UPDATE clinic_letterheads SET active=FALSE, is_default=FALSE, updated_at=now(), updated_by=? WHERE tenant_id=? AND id=?", by, tenantId, id);
    }

    @Transactional
    public void setLogo(UUID tenantId, UUID id, UUID by, byte[] logo) {
        find(tenantId, id);
        List<String> problems = LetterheadRules.problems("x", "x", List.of(), logo);
        if (logo == null || logo.length == 0) problems = List.of("Choose a picture");
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join(". ", problems));
        jdbc.update("UPDATE clinic_letterheads SET logo=?, logo_type=?, updated_at=now(), updated_by=? WHERE tenant_id=? AND id=?",
                logo, LetterheadRules.imageType(logo), by, tenantId, id);
    }

    @Transactional
    public void removeLogo(UUID tenantId, UUID id, UUID by) {
        find(tenantId, id);
        jdbc.update("UPDATE clinic_letterheads SET logo=NULL, logo_type=NULL, updated_at=now(), updated_by=? WHERE tenant_id=? AND id=?", by, tenantId, id);
    }

    public record Logo(byte[] bytes, String type) {}

    @Transactional(readOnly = true)
    public Optional<Logo> logo(UUID tenantId, UUID id) {
        return jdbc.query("SELECT logo, logo_type FROM clinic_letterheads WHERE tenant_id=? AND id=? AND logo IS NOT NULL",
                (rs, i) -> new Logo(rs.getBytes(1), rs.getString(2)), tenantId, id).stream().findFirst();
    }

    /** The letterhead a document is printed on: the chosen one, else the branch's, else the practice default; empty when the practice has set none up. */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolve(UUID tenantId, UUID chosen, String branchName) {
        Row row = LetterheadRules.pick(rows(tenantId, true), chosen, branchName);
        if (row == null) return Optional.empty();
        byte[] logo = logo(tenantId, row.id()).map(Logo::bytes).orElse(null);
        return Optional.of(new Resolved(row.id(), LetterheadRules.toLetterhead(row, logo)));
    }

    private void check(LetterheadRequest r, List<String> lines) {
        if (r == null) throw new IllegalArgumentException("Nothing to save");
        List<String> problems = LetterheadRules.problems(r.name(), r.practiceName(), lines, null);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join(". ", problems));
    }

    private void clearDefault(UUID tenantId) {
        jdbc.update("UPDATE clinic_letterheads SET is_default=FALSE WHERE tenant_id=? AND is_default", tenantId);
    }

    private Row find(UUID tenantId, UUID id) {
        return rows(tenantId, false).stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Letterhead", id.toString()));
    }

    private List<Row> rows(UUID tenantId, boolean activeOnly) {
        return jdbc.query("SELECT id, name, branch_name, practice_name, lines::text, is_default, active FROM clinic_letterheads WHERE tenant_id=? "
                        + (activeOnly ? "AND active " : "") + "ORDER BY is_default DESC, lower(name)",
                (rs, i) -> new Row((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), fromJson(rs.getString(5)), rs.getBoolean(6), rs.getBoolean(7)),
                tenantId);
    }

    private LetterheadResponse response(Row r) {
        return new LetterheadResponse(r.id(), r.name(), r.branchName(), r.practiceName(), r.lines(), r.isDefault(), r.active(), hasLogo(r.id()));
    }

    private boolean hasLogo(UUID id) {
        Boolean b = jdbc.queryForObject("SELECT logo IS NOT NULL FROM clinic_letterheads WHERE id=?", Boolean.class, id);
        return Boolean.TRUE.equals(b);
    }

    private String toJson(List<String> lines) {
        try { return json.writeValueAsString(lines); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private List<String> fromJson(String s) {
        try { return json.readValue(s, new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(); }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
