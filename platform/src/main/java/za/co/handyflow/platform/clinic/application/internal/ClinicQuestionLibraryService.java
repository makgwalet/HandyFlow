package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.question.*;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serves question groups to clinicians, evaluates answers with the rule engine, and stores answers on the
 * consultation. Only ACTIVE content inside its effective dates is served (DEC-CLINIC-001); synthetic demo
 * content is served only when {@code handyflow.clinic.question-library.serve-demo} is true (never in production).
 * Workflow only: nothing here produces a diagnosis.
 */
@Slf4j
@Service
public class ClinicQuestionLibraryService {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate                jdbc;
    private final ObjectMapper                json;
    private final ClinicConsultationRepository consultationRepo;
    private final boolean                     serveDemo;

    public ClinicQuestionLibraryService(JdbcTemplate jdbc, ObjectMapper json,
                                        ClinicConsultationRepository consultationRepo,
                                        @Value("${handyflow.clinic.question-library.serve-demo:false}") boolean serveDemo) {
        this.jdbc = jdbc;
        this.json = json;
        this.consultationRepo = consultationRepo;
        this.serveDemo = serveDemo;
    }

    /** Facts about the patient that rules may use. */
    public record PatientFacts(Integer ageMonths, String sexAtBirth, String pregnancyStatus) {}

    // ── Serving ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GroupView> groupsForVisit(TenantId tenantId, String visitType, UUID patientId) {
        PatientFacts facts = patientId == null ? new PatientFacts(null, null, null) : patientFacts(tenantId, patientId);
        List<VisitMapping> mapping = mappingFor(tenantId, visitType);
        if (mapping.isEmpty()) return List.of();

        List<GroupRow> rows = servedRows(tenantId, mapping.stream().map(VisitMapping::groupCode).toList());
        Map<String, VisitMapping> byCode = mapping.stream().collect(Collectors.toMap(VisitMapping::groupCode, m -> m, (a, b) -> a));
        List<GroupRow> chosen = rows.stream()
                .filter(g -> applicable(g, visitType, facts))
                .sorted(Comparator.comparingInt((GroupRow g) -> byCode.get(g.code()).sortOrder()).thenComparing(GroupRow::code))
                .toList();
        return chosen.stream().map(g -> view(g, byCode.get(g.code()).required())).toList();
    }

    @Transactional(readOnly = true)
    public GroupView servedGroup(TenantId tenantId, String code) {
        return servedRows(tenantId, List.of(code)).stream().findFirst()
                .map(g -> view(g, false))
                .orElseThrow(() -> new ResourceNotFoundException("Question group", code));
    }

    // ── Evaluating ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EvaluationView evaluate(TenantId tenantId, String groupCode, UUID patientId, String visitType,
                                   Map<String, Object> answers) {
        GroupView g = servedGroup(tenantId, groupCode);
        PatientFacts facts = patientId == null ? new PatientFacts(null, null, null) : patientFacts(tenantId, patientId);
        return evaluateView(g, answers, facts, visitType);
    }

    // ── Saving answers on a consultation ─────────────────────────────────────

    @Transactional
    public EvaluationView saveAnswers(TenantId tenantId, UUID consultationId, String groupCode, Map<String, Object> answers) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        if (c.isLocked() || c.isSigned() || c.isAwaitingHandoff()) {
            throw new IllegalStateException("Consultation is " + c.getStatus()
                    + " and its form can no longer be changed here; add an addendum instead.");
        }
        GroupView g = servedGroup(tenantId, groupCode);
        EvaluationView ev = evaluateView(g, answers, patientFacts(tenantId, c.getPatientId()), null);

        Set<String> unknown = new TreeSet<>(answers == null ? Set.of() : answers.keySet());
        unknown.removeAll(g.questions().stream().map(QuestionView::code).collect(Collectors.toSet()));
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unknown questions: " + String.join(", ", unknown));
        if (!ev.problems().isEmpty()) {
            throw new IllegalArgumentException("Some answers are not valid: " + ev.problems().entrySet().stream()
                    .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining("; ")));
        }

        Map<String, Object> existing = readFormData(tenantId, consultationId);
        Map<String, Object> merged = FormData.withGroup(existing, groupCode, g.version(), ev.effectiveAnswers());
        try {
            jdbc.update("UPDATE clinic_consultations SET form_data = ?::jsonb, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                    json.writeValueAsString(merged), consultationId, tenantId.getValue());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the answers.", e);
        }
        return ev;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFormData(TenantId tenantId, UUID consultationId) {
        consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        return readFormData(tenantId, consultationId);
    }

    /**
     * Questionnaires the clinician started on this consultation that still lack required answers, as readable
     * lines ("Group name: question, question"). Groups no longer served are skipped: they cannot be finished.
     */
    @Transactional(readOnly = true)
    public List<String> incompleteGroups(TenantId tenantId, ClinicConsultation c) {
        Map<String, Object> formData = readFormData(tenantId, c.getId());
        Object groups = formData.get("groups");
        if (!(groups instanceof Map<?, ?> started) || started.isEmpty()) return List.of();
        PatientFacts facts = patientFacts(tenantId, c.getPatientId());
        List<String> out = new ArrayList<>();
        for (Object codeObj : started.keySet()) {
            String code = String.valueOf(codeObj);
            GroupView g;
            try { g = servedGroup(tenantId, code); } catch (ResourceNotFoundException e) { continue; }
            EvaluationView ev = evaluateView(g, FormData.answersOf(formData, code), facts, null);
            if (!ev.missingRequired().isEmpty()) {
                Map<String, String> labels = g.questions().stream()
                        .collect(Collectors.toMap(QuestionView::code, QuestionView::label, (a, b) -> a));
                out.add(g.name() + ": " + ev.missingRequired().stream().map(q -> labels.getOrDefault(q, q))
                        .collect(Collectors.joining(", ")));
            }
        }
        return out;
    }

    // ── Internals ────────────────────────────────────────────────────────────

    EvaluationView evaluateView(GroupView g, Map<String, Object> answers, PatientFacts facts, String visitType) {
        List<QuestionDef> defs = g.questions().stream().map(this::toDef).toList();
        List<RedFlagDef> flags = g.redFlags().stream()
                .map(f -> new RedFlagDef(f.code(), f.label(), f.severity(), f.expression(), f.message())).toList();
        RuleContext ctx = new RuleContext(facts.ageMonths(), facts.sexAtBirth(), facts.pregnancyStatus(), visitType);
        Map<String, Object> given = answers == null ? Map.of() : answers;
        EvaluationResult r = QuestionRuleEngine.evaluate(defs, flags, given, ctx);

        Map<String, String> problems = new LinkedHashMap<>();
        for (QuestionDef q : defs) {
            if (!r.visible().contains(q.code())) continue;
            String p = AnswerValidator.problem(q, given.get(q.code()));
            if (p != null) problems.put(q.code(), p);
        }
        List<RedFlagView> flagViews = r.redFlags().stream()
                .map(f -> new RedFlagView(f.code(), f.label(), f.severity(), null, f.message())).toList();
        return new EvaluationView(List.copyOf(r.visible()), List.copyOf(r.required()), List.copyOf(r.disabled()),
                r.warnings(), List.copyOf(r.triggeredGroups()), flagViews, r.hasUrgentFlag(),
                r.effectiveAnswers(), r.missingRequired(), problems);
    }

    private QuestionDef toDef(QuestionView q) {
        List<RuleDef> rules = q.rules().stream()
                .map(r -> new RuleDef(RuleKind.valueOf(r.kind()), r.expression(), r.message(), r.targetGroupCode())).toList();
        return new QuestionDef(q.code(), q.label(), AnswerType.valueOf(q.answerType()), q.options(), q.min(), q.max(),
                q.defaultVisible(), q.defaultRequired(), rules);
    }

    record VisitMapping(String groupCode, int sortOrder, boolean required) {}

    record GroupRow(UUID id, UUID tenantId, String code, int version, String name, String category,
                    Integer minAge, Integer maxAge, List<String> sex, List<String> visitTypes,
                    boolean defaultEnabled, String status, boolean demo) {}

    private List<VisitMapping> mappingFor(TenantId tenantId, String visitType) {
        String sql = "SELECT group_code, sort_order, required FROM clinic_visit_type_group WHERE %s AND visit_type = ? ORDER BY sort_order";
        List<VisitMapping> own = jdbc.query(String.format(sql, "tenant_id = ?"),
                (rs, i) -> new VisitMapping(rs.getString(1), rs.getInt(2), rs.getBoolean(3)), tenantId.getValue(), visitType);
        if (!own.isEmpty()) return own;
        return jdbc.query(String.format(sql, "tenant_id IS NULL"),
                (rs, i) -> new VisitMapping(rs.getString(1), rs.getInt(2), rs.getBoolean(3)), visitType);
    }

    /** One row per code: the tenant's own version beats the platform's; within that, the highest version. */
    private List<GroupRow> servedRows(TenantId tenantId, List<String> codes) {
        if (codes.isEmpty()) return List.of();
        String in = codes.stream().map(c -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(tenantId.getValue());
        args.addAll(codes);
        args.add(serveDemo);
        List<GroupRow> all = jdbc.query("""
            SELECT id, tenant_id, code, version, name, category, applicable_min_age_months, applicable_max_age_months,
                   applicable_sex, applicable_visit_types, default_enabled, status, is_demo
            FROM clinic_question_group
            WHERE (tenant_id = ? OR tenant_id IS NULL)
              AND code IN (""" + in + """
            )
              AND ((status = 'ACTIVE'
                    AND (effective_from IS NULL OR effective_from <= CURRENT_DATE)
                    AND (effective_to   IS NULL OR effective_to   >= CURRENT_DATE))
                   OR (? AND is_demo))
            """, (rs, i) -> new GroupRow(
                    (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"), rs.getString("code"), rs.getInt("version"),
                    rs.getString("name"), rs.getString("category"),
                    (Integer) rs.getObject("applicable_min_age_months"), (Integer) rs.getObject("applicable_max_age_months"),
                    textArray(rs.getArray("applicable_sex")), textArray(rs.getArray("applicable_visit_types")),
                    rs.getBoolean("default_enabled"), rs.getString("status"), rs.getBoolean("is_demo")), args.toArray());

        Map<String, GroupRow> best = new LinkedHashMap<>();
        for (GroupRow g : all) {
            GroupRow cur = best.get(g.code());
            if (cur == null || rank(g) > rank(cur)) best.put(g.code(), g);
        }
        return new ArrayList<>(best.values());
    }

    private static long rank(GroupRow g) { return (g.tenantId() != null ? 1_000_000L : 0L) + g.version(); }

    private static List<String> textArray(java.sql.Array a) {
        if (a == null) return null;
        try { return Arrays.asList((String[]) a.getArray()); } catch (java.sql.SQLException e) { return null; }
    }

    static boolean applicable(GroupRow g, String visitType, PatientFacts facts) {
        if (g.visitTypes() != null && !g.visitTypes().isEmpty() && (visitType == null || !g.visitTypes().contains(visitType))) return false;
        if (g.sex() != null && !g.sex().isEmpty() && (facts.sexAtBirth() == null || !g.sex().contains(facts.sexAtBirth()))) return false;
        if (g.minAge() != null && (facts.ageMonths() == null || facts.ageMonths() < g.minAge())) return false;
        if (g.maxAge() != null && (facts.ageMonths() == null || facts.ageMonths() > g.maxAge())) return false;
        return true;
    }

    private GroupView view(GroupRow g, boolean required) {
        List<QuestionView> questions = new ArrayList<>();
        Map<UUID, List<RuleView>> rulesByQuestion = new HashMap<>();
        jdbc.query("""
            SELECT r.question_id, r.kind, r.expression, r.message, r.target_group_code
            FROM clinic_question_rule r JOIN clinic_question q ON q.id = r.question_id
            WHERE q.group_id = ? ORDER BY r.kind, r.id""", rs -> {
                rulesByQuestion.computeIfAbsent((UUID) rs.getObject(1), k -> new ArrayList<>())
                        .add(new RuleView(rs.getString(2), parse(rs.getString(3)), rs.getString(4), rs.getString(5)));
            }, g.id());
        jdbc.query("""
            SELECT id, code, label, help_text, answer_type, options, min_value, max_value,
                   default_visible, default_required, observation_code
            FROM clinic_question WHERE group_id = ? ORDER BY sort_order, code""", rs -> {
                UUID id = (UUID) rs.getObject("id");
                String opts = rs.getString("options");
                questions.add(new QuestionView(rs.getString("code"), rs.getString("label"), rs.getString("help_text"),
                        rs.getString("answer_type"), opts == null ? List.of() : parseList(opts),
                        rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value"),
                        rs.getBoolean("default_visible"), rs.getBoolean("default_required"), rs.getString("observation_code"),
                        rulesByQuestion.getOrDefault(id, List.of())));
            }, g.id());
        List<RedFlagView> flags = jdbc.query(
                "SELECT code, label, severity, expression, message FROM clinic_red_flag_rule WHERE group_id = ? ORDER BY code",
                (rs, i) -> new RedFlagView(rs.getString(1), rs.getString(2), rs.getString(3), parse(rs.getString(4)), rs.getString(5)),
                g.id());
        return new GroupView(g.id(), g.code(), g.version(), g.name(), g.category(), required, g.defaultEnabled(),
                g.status(), g.demo(), questions, flags);
    }

    private PatientFacts patientFacts(TenantId tenantId, UUID patientId) {
        List<PatientFacts> rows = jdbc.query(
                "SELECT date_of_birth, sex_at_birth, pregnancy_status FROM clinic_patients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                (rs, i) -> {
                    java.sql.Date dob = rs.getDate(1);
                    return new PatientFacts(FormData.ageMonths(dob == null ? null : dob.toLocalDate(), LocalDate.now()),
                            rs.getString(2), rs.getString(3));
                }, patientId, tenantId.getValue());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient", patientId.toString());
        return rows.get(0);
    }

    private Map<String, Object> readFormData(TenantId tenantId, UUID consultationId) {
        List<String> rows = jdbc.query("SELECT form_data::text FROM clinic_consultations WHERE id = ? AND tenant_id = ?",
                (rs, i) -> rs.getString(1), consultationId, tenantId.getValue());
        String raw = rows.isEmpty() ? null : rows.get(0);
        return raw == null ? new LinkedHashMap<>() : parse(raw);
    }

    private Map<String, Object> parse(String raw) {
        try { return json.readValue(raw, MAP); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored rule or form data is not valid JSON.", e); }
    }

    private List<Map<String, Object>> parseList(String raw) {
        try { return json.readValue(raw, LIST_OF_MAPS); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored options are not valid JSON.", e); }
    }

}
