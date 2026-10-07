package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ObservationCode;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoring of a tenant's own question groups. A group is edited only while it is a DRAFT or has had CHANGES_REQUESTED;
 * once it is in review or live it is frozen, and a change means a new version. Platform content (tenant_id NULL) can be
 * read but never edited here. Every write is checked by {@link GroupDefinitionValidator} and recorded in the audit log.
 * Moving a group through review is {@link ClinicContentGovernanceService}'s job, not this class's.
 */
@Service
public class ClinicQuestionAuthoringService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {};
    private static final Set<String> EDITABLE = Set.of("DRAFT", "CHANGES_REQUESTED");
    private static final Set<String> IN_FLIGHT = Set.of("DRAFT", "CLINICAL_REVIEW", "CHANGES_REQUESTED", "APPROVED");
    private static final Set<String> OBSERVATION_CODES =
            Arrays.stream(ObservationCode.values()).map(Enum::name).collect(Collectors.toSet());

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ClinicQuestionAuthoringService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private record Row(UUID id, UUID tenantId, String code, int version, String status, boolean demo) {}

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Full definition of a tenant group or a platform group (read-only), in any status. */
    @Transactional(readOnly = true)
    public GroupDefinitionView definition(TenantId tenantId, UUID groupId) {
        Row r = jdbc.query("SELECT id, tenant_id, code, version, status, is_demo FROM clinic_question_group WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)",
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBoolean(6)),
                groupId, tenantId.getValue()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question group", groupId.toString()));
        return new GroupDefinitionView(r.id(), r.code(), r.version(), r.status(), r.demo(), readDefinition(r.id()));
    }

    private GroupDefinition readDefinition(UUID groupId) {
        var head = jdbc.queryForMap("""
            SELECT name, category, applicable_min_age_months, applicable_max_age_months, applicable_sex,
                   applicable_visit_types, default_enabled, clinical_source, source_version
            FROM clinic_question_group WHERE id = ?""", groupId);

        Map<UUID, List<RuleView>> rules = new HashMap<>();
        jdbc.query("""
            SELECT r.question_id, r.kind, r.expression::text, r.message, r.target_group_code
            FROM clinic_question_rule r JOIN clinic_question q ON q.id = r.question_id
            WHERE q.group_id = ? ORDER BY r.kind, r.id""", rs -> {
                rules.computeIfAbsent((UUID) rs.getObject(1), k -> new ArrayList<>())
                        .add(new RuleView(rs.getString(2), parse(rs.getString(3)), rs.getString(4), rs.getString(5)));
            }, groupId);

        List<QuestionView> questions = jdbc.query("""
            SELECT id, code, label, help_text, answer_type, options::text, min_value, max_value,
                   default_visible, default_required, observation_code
            FROM clinic_question WHERE group_id = ? ORDER BY sort_order, code""",
                (rs, i) -> new QuestionView(rs.getString("code"), rs.getString("label"), rs.getString("help_text"),
                        rs.getString("answer_type"), parseList(rs.getString("options")),
                        rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value"),
                        rs.getBoolean("default_visible"), rs.getBoolean("default_required"), rs.getString("observation_code"),
                        rules.getOrDefault((UUID) rs.getObject("id"), List.of())), groupId);

        List<RedFlagView> flags = jdbc.query("SELECT code, label, severity, expression::text, message FROM clinic_red_flag_rule WHERE group_id = ? ORDER BY code",
                (rs, i) -> new RedFlagView(rs.getString(1), rs.getString(2), rs.getString(3), parse(rs.getString(4)), rs.getString(5)), groupId);

        return new GroupDefinition((String) head.get("name"), (String) head.get("category"),
                (Integer) head.get("applicable_min_age_months"), (Integer) head.get("applicable_max_age_months"),
                textArray(head.get("applicable_sex")), textArray(head.get("applicable_visit_types")),
                Boolean.TRUE.equals(head.get("default_enabled")),
                (String) head.get("clinical_source"), (String) head.get("source_version"), questions, flags);
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional
    public UUID create(TenantId tenantId, UUID actor, String code, GroupDefinition def) {
        String c = code == null ? null : code.trim();
        requireValid(c, def);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_question_group WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId.getValue(), c);
        if (existing != null && existing > 0) {
            throw new IllegalArgumentException("A group with code '" + c + "' already exists. Make a new version of it instead.");
        }
        UUID id = UUID.randomUUID();
        insertGroup(id, tenantId, c, 1, def);
        storeContent(id, def);
        audit(tenantId, id, "DRAFT", "DRAFT", actor, "Created");
        return id;
    }

    @Transactional
    public void replace(TenantId tenantId, UUID groupId, UUID actor, GroupDefinition def) {
        Row r = loadOwn(tenantId, groupId);
        if (!EDITABLE.contains(r.status())) {
            throw new IllegalStateException("A group in " + r.status() + " cannot be edited. Make a new version to change it.");
        }
        requireValid(r.code(), def);
        jdbc.update("""
            UPDATE clinic_question_group SET name = ?, category = ?, applicable_min_age_months = ?, applicable_max_age_months = ?,
                   applicable_sex = ?::text[], applicable_visit_types = ?::text[], default_enabled = ?,
                   clinical_source = ?, source_version = ?, updated_at = NOW() WHERE id = ?""",
                def.name().trim(), blankToNull(def.category()), def.minAgeMonths(), def.maxAgeMonths(),
                arrayLiteral(def.sex()), arrayLiteral(def.visitTypes()), def.defaultEnabled(),
                blankToNull(def.clinicalSource()), blankToNull(def.sourceVersion()), groupId);
        jdbc.update("DELETE FROM clinic_question WHERE group_id = ?", groupId);        // rules cascade
        jdbc.update("DELETE FROM clinic_red_flag_rule WHERE group_id = ?", groupId);
        storeContent(groupId, def);
        audit(tenantId, groupId, r.status(), r.status(), actor, "Definition edited");
    }

    /** Copies a group into a new DRAFT version. Refused while another version of the code is still being worked on. */
    @Transactional
    public UUID newVersion(TenantId tenantId, UUID fromGroupId, UUID actor) {
        Row from = loadOwn(tenantId, fromGroupId);
        List<Row> versions = jdbc.query("SELECT id, tenant_id, code, version, status, is_demo FROM clinic_question_group WHERE tenant_id = ? AND code = ?",
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBoolean(6)),
                tenantId.getValue(), from.code());
        if (versions.stream().anyMatch(v -> IN_FLIGHT.contains(v.status()))) {
            throw new IllegalStateException("Version " + versions.stream().filter(v -> IN_FLIGHT.contains(v.status()))
                    .mapToInt(Row::version).max().orElse(0) + " of this group is still being worked on. Finish or retire it first.");
        }
        int next = versions.stream().mapToInt(Row::version).max().orElse(0) + 1;
        GroupDefinition def = readDefinition(from.id());
        UUID id = UUID.randomUUID();
        insertGroup(id, tenantId, from.code(), next, def);
        storeContent(id, def);
        audit(tenantId, id, "DRAFT", "DRAFT", actor, "New version " + next + " copied from version " + from.version());
        return id;
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private Row loadOwn(TenantId tenantId, UUID groupId) {
        return jdbc.query("SELECT id, tenant_id, code, version, status, is_demo FROM clinic_question_group WHERE id = ? AND tenant_id = ?",
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBoolean(6)),
                groupId, tenantId.getValue()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question group", groupId.toString()));
    }

    private static void requireValid(String code, GroupDefinition def) {
        List<String> problems = GroupDefinitionValidator.problems(code, def, OBSERVATION_CODES);
        if (!problems.isEmpty()) throw new IllegalArgumentException("The group is not valid: " + String.join(" ", problems));
    }

    private void insertGroup(UUID id, TenantId tenantId, String code, int version, GroupDefinition d) {
        jdbc.update("""
            INSERT INTO clinic_question_group (id, tenant_id, code, version, name, category, applicable_min_age_months,
                applicable_max_age_months, applicable_sex, applicable_visit_types, default_enabled, status, clinical_source, source_version, is_demo)
            VALUES (?,?,?,?,?,?,?,?,?::text[],?::text[],?,'DRAFT',?,?,FALSE)""",
                id, tenantId.getValue(), code, version, d.name().trim(), blankToNull(d.category()), d.minAgeMonths(),
                d.maxAgeMonths(), arrayLiteral(d.sex()), arrayLiteral(d.visitTypes()), d.defaultEnabled(),
                blankToNull(d.clinicalSource()), blankToNull(d.sourceVersion()));
    }

    private void storeContent(UUID groupId, GroupDefinition d) {
        int order = 0;
        for (QuestionView q : d.questions()) {
            UUID qid = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO clinic_question (id, group_id, code, label, help_text, answer_type, options, min_value, max_value,
                    default_visible, default_required, observation_code, sort_order)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)""",
                    qid, groupId, q.code(), q.label().trim(), blankToNull(q.helpText()), q.answerType(),
                    q.options() == null || q.options().isEmpty() ? null : write(q.options()),
                    q.min(), q.max(), q.defaultVisible(), q.defaultRequired(), blankToNull(q.observationCode()), ++order);
            for (RuleView r : q.rules() == null ? List.<RuleView>of() : q.rules()) {
                jdbc.update("INSERT INTO clinic_question_rule (id, question_id, kind, expression, message, target_group_code) VALUES (?,?,?,?::jsonb,?,?)",
                        UUID.randomUUID(), qid, r.kind(), write(r.expression()), blankToNull(r.message()), blankToNull(r.targetGroupCode()));
            }
        }
        for (RedFlagView f : d.redFlags() == null ? List.<RedFlagView>of() : d.redFlags()) {
            jdbc.update("INSERT INTO clinic_red_flag_rule (id, group_id, code, label, severity, expression, message) VALUES (?,?,?,?,?,?::jsonb,?)",
                    UUID.randomUUID(), groupId, f.code(), f.label().trim(), f.severity(), write(f.expression()), blankToNull(f.message()));
        }
    }

    private void audit(TenantId tenantId, UUID groupId, String from, String to, UUID actor, String note) {
        jdbc.update("INSERT INTO clinic_content_audit (id, tenant_id, group_id, from_status, to_status, actor_user_id, note) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId.getValue(), groupId, from, to, actor, note);
    }

    private String write(Object o) {
        try { return json.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Could not store a rule or option list.", e); }
    }

    private Map<String, Object> parse(String s) {
        try { return s == null ? null : json.readValue(s, MAP); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored rule is not valid JSON.", e); }
    }

    private List<Map<String, Object>> parseList(String s) {
        try { return s == null ? null : json.readValue(s, LIST_OF_MAP); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored options are not valid JSON.", e); }
    }

    /** A Postgres text[] literal with every element quoted; null for none, so the column stays NULL (meaning "all"). */
    static String arrayLiteral(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static List<String> textArray(Object a) {
        if (!(a instanceof java.sql.Array arr)) return null;
        try { return Arrays.asList((String[]) arr.getArray()); } catch (java.sql.SQLException e) { return null; }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
