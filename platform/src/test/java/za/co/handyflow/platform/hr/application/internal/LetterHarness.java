package za.co.handyflow.platform.hr.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.hr.domain.model.DisciplineRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrErEvent;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.hr.domain.model.HrLetterEvent;
import za.co.handyflow.platform.hr.domain.model.HrLetterTemplate;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** The letter service wired to in-memory stand-ins for the repositories and the file store. */
final class LetterHarness {

    static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final UUID HR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final Set<String> ISSUER = Set.of("HR_LETTER_READ", "HR_LETTER_ISSUE", "HR_LETTER_MANAGE", "HR_ER_READ", "HR_ER_MANAGE");
    static final Set<String> READER = Set.of("HR_LETTER_READ");
    static final Set<String> NOTHING = Set.of();

    /** Keeps files in memory, and can be told to fail or to hand back something different. */
    static final class MemoryFiles implements FileStorageService {
        final Map<String, byte[]> stored = new HashMap<>();
        boolean failToStore;
        boolean tamper;

        @Override public String store(String prefix, String name, String type, byte[] content) throws IOException {
            if (failToStore) throw new IOException("disk full");
            String key = prefix + "/" + UUID.randomUUID() + "-" + name;
            stored.put(key, content);
            return key;
        }

        @Override public byte[] retrieve(String key) throws IOException {
            byte[] b = stored.get(key);
            if (b == null) throw new IOException("missing " + key);
            if (tamper) return "not the letter".getBytes();
            return b;
        }

        @Override public void delete(String key) { stored.remove(key); }
    }

    final HrLetterTemplateRepository templateRepo = mock(HrLetterTemplateRepository.class);
    final HrLetterRepository letterRepo = mock(HrLetterRepository.class);
    final HrLetterEventRepository letterEvents = mock(HrLetterEventRepository.class);
    final HrEmployeeRepository employees = mock(HrEmployeeRepository.class);
    final HrErCaseRepository cases = mock(HrErCaseRepository.class);
    final HrErChargeRepository charges = mock(HrErChargeRepository.class);
    final HrErStepRepository steps = mock(HrErStepRepository.class);
    final HrErEventRepository caseEvents = mock(HrErEventRepository.class);
    final HrErPolicyRepository policyRepo = mock(HrErPolicyRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final MemoryFiles files = new MemoryFiles();
    final za.co.handyflow.platform.notifications.application.internal.NotificationService notifications =
            mock(za.co.handyflow.platform.notifications.application.internal.NotificationService.class);

    final List<HrLetterTemplate> savedTemplates = new ArrayList<>();
    final List<HrLetter> savedLetters = new ArrayList<>();
    final List<HrLetterEvent> savedEvents = new ArrayList<>();
    final List<HrErEvent> savedCaseEvents = new ArrayList<>();

    final ErPolicyService policy = new ErPolicyService(policyRepo);
    final ErCaseSupport support = new ErCaseSupport(cases, steps, caseEvents, employees, jdbc);
    final LetterContext context = new LetterContext(employees, charges, policy, jdbc);
    final LetterPdfGenerator pdf = new LetterPdfGenerator();
    final LetterService service = new LetterService(templateRepo, letterRepo, letterEvents, employees, cases, support, context, pdf, files, jdbc, notifications);

    private int number = 6;

    LetterHarness() {
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenAnswer(inv -> ++number);

        lenient().when(templateRepo.forTenant(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(savedTemplates));
        lenient().when(templateRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(1);
            return savedTemplates.stream().filter(t -> t.getId().equals(id)).findFirst();
        });
        lenient().when(templateRepo.save(any(HrLetterTemplate.class))).thenAnswer(inv -> {
            HrLetterTemplate t = inv.getArgument(0);
            if (!savedTemplates.contains(t)) savedTemplates.add(t);
            return t;
        });

        lenient().when(letterRepo.forTenant(eq(TENANT))).thenAnswer(inv -> newestFirst(savedLetters));
        lenient().when(letterRepo.forEmployee(eq(TENANT), any())).thenAnswer(inv -> {
            UUID emp = inv.getArgument(1);
            return newestFirst(savedLetters.stream().filter(l -> l.getEmployeeId().equals(emp)).toList());
        });
        lenient().when(letterRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(1);
            return savedLetters.stream().filter(l -> l.getId().equals(id)).findFirst();
        });
        lenient().when(letterRepo.save(any(HrLetter.class))).thenAnswer(inv -> {
            HrLetter l = inv.getArgument(0);
            if (!savedLetters.contains(l)) savedLetters.add(l);
            return l;
        });

        lenient().when(letterEvents.save(any(HrLetterEvent.class))).thenAnswer(inv -> {
            HrLetterEvent e = inv.getArgument(0);
            savedEvents.add(e);
            return e;
        });
        lenient().when(letterEvents.forLetter(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return savedEvents.stream().filter(e -> e.getLetterId().equals(id)).toList();
        });
        lenient().when(caseEvents.save(any(HrErEvent.class))).thenAnswer(inv -> {
            HrErEvent e = inv.getArgument(0);
            savedCaseEvents.add(e);
            return e;
        });
    }

    private static List<HrLetter> newestFirst(List<HrLetter> in) {
        List<HrLetter> out = new ArrayList<>(in);
        java.util.Collections.reverse(out);
        return out;
    }

    static LocalDate today() { return LocalDate.now(DisciplineRules.ZONE); }

    /** An employee who exists in this company. */
    UUID employee(String first, String last) {
        HrEmployee e = mock(HrEmployee.class);
        UUID id = UUID.randomUUID();
        lenient().when(e.getId()).thenReturn(id);
        lenient().when(e.getFirstName()).thenReturn(first);
        lenient().when(e.getLastName()).thenReturn(last);
        lenient().when(e.getFullName()).thenReturn(first + " " + last);
        lenient().when(e.getEmployeeNumber()).thenReturn("E-" + id.toString().substring(0, 4));
        lenient().when(e.getJobTitle()).thenReturn("Clerk");
        lenient().when(e.getDepartment()).thenReturn("Admin");
        lenient().when(e.getStartDate()).thenReturn(LocalDate.of(2024, 3, 1));
        lenient().when(employees.findActiveById(TENANT, id)).thenReturn(Optional.of(e));
        return id;
    }

    /** Makes this person the one the signed-in user is. */
    UUID signedInAs(UUID employeeId) {
        UUID user = UUID.randomUUID();
        HrEmployee e = employees.findActiveById(TENANT, employeeId).orElseThrow();
        lenient().when(employees.findByUserId(TENANT, user)).thenReturn(Optional.of(e));
        return user;
    }

    HrErCase discipline(UUID employeeId, boolean confidential) {
        LocalDate today = today();
        HrErCase c = HrErCase.open(TENANT.getValue(), "ER-2026-0001", "DISCIPLINARY", "LATENESS", employeeId, "Late three times", "Arrived after 09:00",
                today.minusDays(10), today.minusDays(9), confidential, HR_USER, HR_USER);
        c.issueNotice(Instant.now(), today.plusDays(5), LocalTime.of(9, 0), "Boardroom", employeeId == null ? null : UUID.randomUUID(), null);
        lenient().when(cases.findByTenantAndId(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }
}
