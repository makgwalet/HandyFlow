package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.LetterDtos.*;
import lombok.extern.slf4j.Slf4j;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

/**
 * The Letter & Document Generation Centre: templates, letters made from them, the PDF kept on the employee's file, and the employee's
 * acknowledgement. What a letter says is the template's wording and the facts on the record; this service only fills it in, keeps it, and
 * makes sure the right people can see it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LetterService {

    private final HrLetterTemplateRepository templateRepo;
    private final HrLetterRepository letterRepo;
    private final HrLetterEventRepository eventRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrErCaseRepository caseRepo;
    private final ErCaseSupport support;
    private final LetterContext context;
    private final LetterPdfGenerator pdfGenerator;
    private final FileStorageService files;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    /** A file ready to send. */
    public record Pdf(String fileName, byte[] bytes) {}

    // ── Templates ─────────────────────────────────────────────────────────────────────────────────

    /** The company's templates. A company that has none yet gets the standard set the first time it looks. */
    @Transactional
    public Templates templates(TenantId tenantId, Set<String> held) {
        requireRead(held);
        List<HrLetterTemplate> have = new ArrayList<>(templateRepo.forTenant(tenantId));
        Set<String> codes = new HashSet<>();
        for (HrLetterTemplate t : have) codes.add(t.getCode());
        boolean added = false;
        for (LetterDefaults.Template d : LetterDefaults.ALL) {
            if (codes.contains(d.code())) continue;
            HrLetterTemplate fresh = HrLetterTemplate.fromDefault(tenantId.getValue(), d);
            templateRepo.save(fresh);
            have.add(fresh);
            added = true;
        }
        if (added) have.sort(Comparator.comparing(HrLetterTemplate::getName));
        List<FieldView> fields = LetterRules.FIELDS.stream().map(f -> new FieldView(f.key(), f.label(), f.group())).toList();
        return new Templates(have.stream().map(this::view).toList(), fields);
    }

    @Transactional
    public TemplateView createTemplate(TenantId tenantId, TemplateRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        fail(LetterRules.templateProblem(req.name(), req.category(), req.subject(), req.body()));
        Set<String> taken = new HashSet<>();
        for (HrLetterTemplate t : templateRepo.forTenant(tenantId)) taken.add(t.getCode());
        for (LetterDefaults.Template d : LetterDefaults.ALL) taken.add(d.code());
        String code = LetterRules.codeFrom(req.name(), taken);
        HrLetterTemplate t = HrLetterTemplate.custom(tenantId.getValue(), code, req.name(), req.category(), req.subject(),
                req.body(), Boolean.TRUE.equals(req.requiresAck()), userId);
        templateRepo.save(t);
        return view(t);
    }

    @Transactional
    public TemplateView saveTemplate(TenantId tenantId, UUID id, TemplateRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrLetterTemplate t = template(tenantId, id);
        boolean standard = LetterDefaults.byCode(t.getCode()).isPresent();
        // A standard template keeps its category, so the case screens keep offering it in the right place.
        String category = standard ? t.getCategory() : req.category();
        fail(LetterRules.templateProblem(req.name(), category, req.subject(), req.body()));
        t.edit(req.name(), category, req.subject(), req.body(), Boolean.TRUE.equals(req.requiresAck()), req.active() == null || req.active(), userId);
        templateRepo.save(t);
        return view(t);
    }

    /** Puts a standard template back to the wording it came with. */
    @Transactional
    public TemplateView restoreTemplate(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        requireManage(held);
        HrLetterTemplate t = template(tenantId, id);
        LetterDefaults.Template d = LetterDefaults.byCode(t.getCode())
                .orElseThrow(() -> new IllegalStateException("Only the standard templates have standard wording to go back to."));
        t.restore(d, userId);
        templateRepo.save(t);
        return view(t);
    }

    // ── Making a letter ───────────────────────────────────────────────────────────────────────────

    /** Fills a template in for one employee (and one case) and keeps the result as a draft. */
    @Transactional
    public Detail prepare(TenantId tenantId, PrepareRequest req, UUID userId, Set<String> held) {
        requireIssue(held);
        if (req.templateId() == null) throw new IllegalArgumentException("Choose the template.");
        HrLetterTemplate t = template(tenantId, req.templateId());
        if (!t.isActive()) throw new IllegalStateException("That template is switched off.");

        HrErCase c = null;
        HrEmployee emp;
        if (req.caseId() != null) {
            c = support.findToAct(tenantId, req.caseId(), held, userId);
            if (req.employeeId() != null && !req.employeeId().equals(c.getEmployeeId()))
                throw new IllegalArgumentException("That case is about a different employee.");
            emp = support.employee(tenantId, c.getEmployeeId());
        } else {
            emp = support.employee(tenantId, req.employeeId());
            refuseSelf(tenantId, emp.getId(), userId);
        }

        HrLetter l = makeDraft(tenantId, t, emp, c, userId);
        return detail(tenantId, l, held, userId);
    }

    /** Fills a template in for one employee (and maybe a case) and keeps it as a draft. */
    private HrLetter makeDraft(TenantId tenantId, HrLetterTemplate t, HrEmployee emp, HrErCase c, UUID userId) {
        LocalDate today = support.today();
        Map<String, String> values = context.build(tenantId, emp, c, today);
        LetterRules.Rendered subject = LetterRules.render(t.getSubject(), values);
        LetterRules.Rendered body = LetterRules.render(t.getBody(), values);
        String subjectText = subject.text().length() > 200 ? subject.text().substring(0, 200) : subject.text();

        HrLetter l = HrLetter.draft(tenantId.getValue(), nextNumber(tenantId), t, emp.getId(), c == null ? null : c.getId(), subjectText, body.text(), userId);
        letterRepo.save(l);
        event(l, "PREPARED", "From \"" + t.getName() + "\" (revision " + t.getRevision() + ")", userId);
        return l;
    }

    @Transactional
    public Detail updateDraft(TenantId tenantId, UUID id, DraftRequest req, UUID userId, Set<String> held) {
        requireIssue(held);
        HrLetter l = findToAct(tenantId, id, held, userId);
        if (!LetterRules.mayEdit(l.getStatus())) throw new IllegalStateException("An issued letter cannot be changed. Void it and make a new one.");
        fail(LetterRules.draftProblem(req.subject(), req.body()));
        l.edit(req.subject(), req.body());
        letterRepo.save(l);
        event(l, "EDITED", null, userId);
        return detail(tenantId, l, held, userId);
    }

    /** Makes the PDF, keeps it, and gives the letter to the employee. */
    @Transactional
    public Detail issue(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        requireIssue(held);
        HrLetter l = findToAct(tenantId, id, held, userId);
        issueOne(tenantId, l, userId);
        return detail(tenantId, l, held, userId);
    }

    private void issueOne(TenantId tenantId, HrLetter l, UUID userId) {
        fail(LetterRules.issueProblem(l.getStatus(), l.getSubject(), l.getBody()));
        LocalDate today = support.today();
        String fileName = l.getNumber() + ".pdf";
        byte[] bytes = pdfGenerator.generate(context.letterhead(tenantId), l.getNumber(), today, l.getSubject(), l.getBody());
        String key;
        try {
            key = files.store("hr/letters/" + tenantId.getValue() + "/" + l.getEmployeeId(), fileName, "application/pdf", bytes);
        } catch (IOException e) {
            throw new IllegalStateException("The letter could not be saved, so it was not issued. Try again.", e);
        }
        l.issue(today, userId, key, fileName, sha256(bytes));
        letterRepo.save(l);
        event(l, "ISSUED", l.isRequiresAck() ? "Confirmation of receipt requested" : null, userId);
        caseEvent(tenantId, l, "LETTER_ISSUED", l.getSubject() + " (" + l.getNumber() + ")", true, userId);
        tellEmployee(tenantId, l);
    }

    /**
     * Tells the employee a letter is waiting. The message gives the reference and nothing of what the letter says, because it may
     * concern discipline or health and an email is not a private place. Best effort: a failure here never undoes the issue.
     */
    private void tellEmployee(TenantId tenantId, HrLetter l) {
        try {
            HrEmployee emp = employeeRepo.findActiveById(tenantId, l.getEmployeeId()).orElse(null);
            if (emp == null || emp.getEmail() == null || emp.getEmail().isBlank()) return;
            Recipient to = emp.getUserId() != null ? Recipient.user(emp.getUserId(), emp.getFullName(), emp.getEmail(), emp.getPhone())
                    : Recipient.external(emp.getFullName(), emp.getEmail(), emp.getPhone());
            notifications.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.LETTER_ISSUED)
                    .title("A letter from HR is waiting for you")
                    .message(LetterRules.issuedMessage(l.getNumber(), l.isRequiresAck()))
                    .actionUrl("/hr/my-letters")
                    .sourceModule("hr")
                    .sourceEntityId(l.getId().toString())
                    .recipient(to)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Letter {} was issued but the employee could not be notified: {}", l.getNumber(), e.getMessage());
        }
    }

    @Transactional
    public Detail voidLetter(TenantId tenantId, UUID id, VoidRequest req, UUID userId, Set<String> held) {
        requireIssue(held);
        HrLetter l = findToAct(tenantId, id, held, userId);
        fail(LetterRules.voidProblem(l.getStatus(), req.reason()));
        boolean wasIssued = LetterRules.ISSUED.equals(l.getStatus());
        l.voidIt(userId, req.reason());
        letterRepo.save(l);
        event(l, "VOIDED", req.reason().trim(), userId);
        caseEvent(tenantId, l, "LETTER_VOIDED", l.getNumber() + ": " + req.reason().trim(), wasIssued, userId);
        return detail(tenantId, l, held, userId);
    }

    // ── Many at once ──────────────────────────────────────────────────────────────────────────────

    /**
     * Makes the same general or employment template for many employees, each as its own draft. Nothing is issued: HR reads the
     * drafts and issues them. An employee who cannot be done (HR's own user, someone who left) is reported and the rest go ahead.
     */
    @Transactional
    public BulkPrepared prepareBulk(TenantId tenantId, BulkPrepareRequest req, UUID userId, Set<String> held) {
        requireIssue(held);
        if (req.templateId() == null) throw new IllegalArgumentException("Choose the template.");
        List<UUID> ids = req.employeeIds() == null ? List.of() : req.employeeIds().stream().filter(Objects::nonNull).distinct().toList();
        HrLetterTemplate t = template(tenantId, req.templateId());
        if (!t.isActive()) throw new IllegalStateException("That template is switched off.");
        fail(LetterRules.bulkProblem(t.getCategory(), t.getSubject(), t.getBody(), ids.size()));
        List<BulkItem> out = new ArrayList<>();
        int made = 0;
        for (UUID employeeId : ids) {
            String name = null;
            try {
                HrEmployee emp = support.employee(tenantId, employeeId);
                name = emp.getFullName();
                refuseSelf(tenantId, emp.getId(), userId);
                if ("TERMINATED".equals(emp.getStatus())) throw new IllegalStateException(name + " no longer works here.");
                HrLetter l = makeDraft(tenantId, t, emp, null, userId);
                out.add(new BulkItem(employeeId, name, l.getId(), l.getNumber(), LetterRules.gaps(l.getSubject(), l.getBody()), null));
                made++;
            } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException | ResourceNotFoundException e) {
                out.add(new BulkItem(employeeId, name, null, null, List.of(), e.getMessage()));
            }
        }
        return new BulkPrepared(out, made, out.size() - made);
    }

    /** Issues many drafts. A draft with gaps, one that is not a draft any more, or one about HR's own user is reported and the rest go ahead. */
    @Transactional
    public BulkIssued issueBulk(TenantId tenantId, BulkIssueRequest req, UUID userId, Set<String> held) {
        requireIssue(held);
        List<UUID> ids = req.letterIds() == null ? List.of() : req.letterIds().stream().filter(Objects::nonNull).distinct().toList();
        fail(LetterRules.bulkIssueProblem(ids.size()));
        List<BulkIssueItem> out = new ArrayList<>();
        int issued = 0;
        for (UUID id : ids) {
            String number = null, name = null;
            try {
                HrLetter l = findToAct(tenantId, id, held, userId);
                number = l.getNumber();
                name = support.nameOf(tenantId, l.getEmployeeId());
                issueOne(tenantId, l, userId);
                out.add(new BulkIssueItem(id, number, name, null));
                issued++;
            } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException | ResourceNotFoundException e) {
                out.add(new BulkIssueItem(id, number, name, e.getMessage()));
            }
        }
        return new BulkIssued(out, issued, out.size() - issued);
    }

    // ── Reading, for HR ───────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Row> list(TenantId tenantId, UUID employeeId, UUID caseId, String status, Set<String> held, UUID userId) {
        requireRead(held);
        Map<UUID, Optional<HrErCase>> cases = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        List<Row> out = new ArrayList<>();
        for (HrLetter l : letterRepo.forTenant(tenantId)) {
            if (employeeId != null && !employeeId.equals(l.getEmployeeId())) continue;
            if (caseId != null && !caseId.equals(l.getCaseId())) continue;
            if (status != null && !status.isBlank() && !status.equals(l.getStatus())) continue;
            Optional<HrErCase> c = l.getCaseId() == null ? Optional.empty()
                    : cases.computeIfAbsent(l.getCaseId(), cid -> caseRepo.findByTenantAndId(tenantId, cid));
            if (l.getCaseId() != null && (c.isEmpty() || !ErRules.canSee(held, userId, c.get().isConfidential(), c.get().getOwnerUserId()))) continue;
            out.add(row(l, names.computeIfAbsent(l.getEmployeeId(), eid -> support.nameOf(tenantId, eid)), c.map(HrErCase::getCaseNumber).orElse(null)));
            if (out.size() >= 300) break;
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Detail get(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        requireRead(held);
        return detail(tenantId, find(tenantId, id, held, userId), held, userId);
    }

    /** The PDF of an issued letter. The stored file is checked against the fingerprint taken when it was issued. */
    @Transactional(readOnly = true)
    public Pdf download(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        requireRead(held);
        return pdf(find(tenantId, id, held, userId));
    }

    // ── For the employee ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Mine> mine(TenantId tenantId, UUID userId) {
        HrEmployee me = support.myEmployee(tenantId, userId);
        return letterRepo.forEmployee(tenantId, me.getId()).stream().filter(l -> LetterRules.ISSUED.equals(l.getStatus())).map(this::mineView).toList();
    }

    @Transactional(readOnly = true)
    public Mine mineOne(TenantId tenantId, UUID id, UUID userId) {
        return mineView(own(tenantId, id, userId));
    }

    @Transactional(readOnly = true)
    public Pdf minePdf(TenantId tenantId, UUID id, UUID userId) {
        return pdf(own(tenantId, id, userId));
    }

    /** The employee confirms they received the letter. This is a record of receipt, not agreement. */
    @Transactional
    public Mine acknowledge(TenantId tenantId, UUID id, UUID userId) {
        HrLetter l = own(tenantId, id, userId);
        if (!l.isRequiresAck()) throw new IllegalStateException("This letter does not ask for confirmation.");
        if (l.getAcknowledgedAt() != null) throw new IllegalStateException("You have already confirmed that you received this letter.");
        if (!LetterRules.mayAcknowledge(l.getStatus(), l.isRequiresAck(), l.getAcknowledgedAt())) throw new IllegalStateException("This letter cannot be confirmed.");
        l.acknowledge(userId);
        letterRepo.save(l);
        event(l, "ACKNOWLEDGED", "Confirmed receipt of the file with fingerprint " + l.getFileSha256().substring(0, 12), userId);
        caseEvent(tenantId, l, "LETTER_ACKNOWLEDGED", l.getNumber() + ": employee confirmed receipt", true, userId);
        return mineView(l);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────

    private HrLetterTemplate template(TenantId tenantId, UUID id) {
        return templateRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Template", String.valueOf(id)));
    }

    /** A letter the person may see. One that belongs to a confidential case they may not see looks the same as one that does not exist. */
    private HrLetter find(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrLetter l = letterRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Letter", String.valueOf(id)));
        if (l.getCaseId() != null) support.find(tenantId, l.getCaseId(), held, userId);
        return l;
    }

    /** A letter to act on. Nobody works on a letter about themselves. */
    private HrLetter findToAct(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        HrLetter l = find(tenantId, id, held, userId);
        refuseSelf(tenantId, l.getEmployeeId(), userId);
        return l;
    }

    private HrLetter own(TenantId tenantId, UUID id, UUID userId) {
        HrEmployee me = support.myEmployee(tenantId, userId);
        HrLetter l = letterRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Letter", String.valueOf(id)));
        if (!me.getId().equals(l.getEmployeeId()) || !LetterRules.ISSUED.equals(l.getStatus())) throw new ResourceNotFoundException("Letter", String.valueOf(id));
        return l;
    }

    private void refuseSelf(TenantId tenantId, UUID employeeId, UUID userId) {
        if (userId == null) return;
        employeeRepo.findByUserId(tenantId, userId).ifPresent(e -> {
            if (e.getId().equals(employeeId)) throw new AccessDeniedException("You cannot work on a letter about yourself.");
        });
    }

    private Pdf pdf(HrLetter l) {
        if (l.getFileKey() == null) throw new IllegalStateException("This letter has no file yet. Issue it first.");
        byte[] bytes;
        try {
            bytes = files.retrieve(l.getFileKey());
        } catch (IOException e) {
            throw new IllegalStateException("The file for this letter could not be found.", e);
        }
        if (!sha256(bytes).equals(l.getFileSha256()))
            throw new IllegalStateException("The stored file no longer matches the letter that was issued. It was not sent.");
        return new Pdf(l.getFileName(), bytes);
    }

    private String nextNumber(TenantId tenantId) {
        int year = support.today().getYear();
        Integer n = jdbc.queryForObject(
                "INSERT INTO hr_letter_counters (tenant_id, year, last_no) VALUES (?, ?, 1) "
                        + "ON CONFLICT (tenant_id, year) DO UPDATE SET last_no = hr_letter_counters.last_no + 1 RETURNING last_no",
                Integer.class, tenantId.getValue(), year);
        return String.format("LT-%d-%04d", year, n == null ? 1 : n);
    }

    private void event(HrLetter l, String action, String note, UUID by) {
        eventRepo.save(HrLetterEvent.of(l.getTenantId(), l.getId(), action, note, by));
    }

    /** A letter that belongs to a case shows on the case's timeline too. */
    private void caseEvent(TenantId tenantId, HrLetter l, String action, String note, boolean visibleToEmployee, UUID by) {
        if (l.getCaseId() == null) return;
        caseRepo.findByTenantAndId(tenantId, l.getCaseId()).ifPresent(c -> support.event(c, action, note, visibleToEmployee, by));
    }

    private TemplateView view(HrLetterTemplate t) {
        Set<String> fields = new LinkedHashSet<>(LetterRules.fieldsIn(t.getSubject()));
        fields.addAll(LetterRules.fieldsIn(t.getBody()));
        return new TemplateView(t.getId(), t.getCode(), t.getName(), t.getCategory(), t.getSubject(), t.getBody(), t.isRequiresAck(), t.isActive(),
                t.isCustomised(), LetterDefaults.byCode(t.getCode()).isPresent(), t.getRevision(), new ArrayList<>(fields));
    }

    private Row row(HrLetter l, String employeeName, String caseNumber) {
        return new Row(l.getId(), l.getNumber(), l.getSubject(), l.getTemplateName(), l.getEmployeeId(), employeeName, l.getCaseId(), caseNumber,
                l.getStatus(), l.getIssuedOn(), l.isRequiresAck(), l.getAcknowledgedAt(), l.getCreatedAt());
    }

    private Detail detail(TenantId tenantId, HrLetter l, Set<String> held, UUID userId) {
        String caseNumber = l.getCaseId() == null ? null : caseRepo.findByTenantAndId(tenantId, l.getCaseId()).map(HrErCase::getCaseNumber).orElse(null);
        Map<UUID, String> users = new HashMap<>();
        List<EventView> events = eventRepo.forLetter(l.getId()).stream()
                .map(e -> new EventView(e.getAction(), e.getNote(), e.getDoneBy() == null ? null
                        : users.computeIfAbsent(e.getDoneBy(), u -> employeeRepo.findByUserId(tenantId, u).map(HrEmployee::getFullName).orElse("HR")), e.getDoneAt()))
                .toList();
        List<String> actions = LetterRules.canIssue(held) ? LetterRules.actions(l.getStatus()) : (l.getFileKey() != null ? List.of("DOWNLOAD") : List.of());
        return new Detail(row(l, support.nameOf(tenantId, l.getEmployeeId()), caseNumber), l.getBody(), LetterRules.gaps(l.getSubject(), l.getBody()), events,
                actions, l.getFileName(), l.getFileSha256(), l.getVoidReason(), l.getTemplateRevision());
    }

    private Mine mineView(HrLetter l) {
        return new Mine(l.getId(), l.getNumber(), l.getSubject(), l.getBody(), l.getTemplateName(), l.getIssuedOn(), l.isRequiresAck(), l.getAcknowledgedAt(),
                LetterRules.mayAcknowledge(l.getStatus(), l.isRequiresAck(), l.getAcknowledgedAt()));
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }

    private static void requireRead(Set<String> held) {
        if (!LetterRules.canRead(held)) throw new AccessDeniedException("You do not have permission to see HR letters.");
    }

    private static void requireManage(Set<String> held) {
        if (!LetterRules.canManage(held)) throw new AccessDeniedException("You do not have permission to change letter templates.");
    }

    private static void requireIssue(Set<String> held) {
        if (!LetterRules.canIssue(held)) throw new AccessDeniedException("You do not have permission to prepare or issue HR letters.");
    }
}
