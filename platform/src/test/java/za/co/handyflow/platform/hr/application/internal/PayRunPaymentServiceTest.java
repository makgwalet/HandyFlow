package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayRunPaymentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Making up, downloading, confirming and returning bank payments. Uses hand-written fakes for the repositories. */
class PayRunPaymentServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final HrPayRun run = HrPayRun.create(tenant, "PR00005", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 25), 2027);
    private final List<HrPayslip> slips = new ArrayList<>();
    private final Map<UUID, HrEmployee> emps = new HashMap<>();
    private final List<HrPayRunPayment> payments = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final HrPayRunRepository payRunRepo = fake(HrPayRunRepository.class, (n, a) ->
            "findByTenantAndId".equals(n) ? (run.getId().equals(a[1]) ? Optional.of(run) : Optional.empty()) : NOPE);
    private final HrPayslipRepository payslipRepo = fake(HrPayslipRepository.class, (n, a) ->
            "findByPayRun".equals(n) ? slips : NOPE);
    @SuppressWarnings("unchecked")
    private final HrEmployeeRepository employeeRepo = fake(HrEmployeeRepository.class, (n, a) -> {
        if (!"findAllByIds".equals(n)) return NOPE;
        List<HrEmployee> out = new ArrayList<>();
        for (UUID id : (Collection<UUID>) a[1]) if (emps.containsKey(id)) out.add(emps.get(id));
        return out;
    });
    @SuppressWarnings("unchecked")
    private final HrPayRunPaymentRepository paymentRepo = fake(HrPayRunPaymentRepository.class, (n, a) -> switch (n) {
        case "forRun" -> new ArrayList<>(payments);
        case "findByTenantAndId" -> payments.stream().filter(p -> p.getId().equals(a[1])).findFirst();
        case "saveAll" -> { for (HrPayRunPayment p : (Iterable<HrPayRunPayment>) a[0]) if (!payments.contains(p)) payments.add(p); yield a[0]; }
        case "save" -> { if (!payments.contains(a[0])) payments.add((HrPayRunPayment) a[0]); yield a[0]; }
        case "deleteAll" -> { payments.removeAll((Collection<?>) a[0]); yield null; }
        default -> NOPE;
    });
    private final HrPayRunEventRepository eventRepo = fake(HrPayRunEventRepository.class, (n, a) -> {
        if (!"save".equals(n)) return NOPE;
        events.add(((HrPayRunEvent) a[0]).getAction() + ":" + ((HrPayRunEvent) a[0]).getNote());
        return a[0];
    });
    private HrPayrollSettings settings;
    private final HrPayrollSettingsRepository settingsRepo = fake(HrPayrollSettingsRepository.class, (n, a) ->
            "findById".equals(n) ? Optional.ofNullable(settings) : NOPE);
    private final PayRunPaymentService service = new PayRunPaymentService(payRunRepo, payslipRepo, employeeRepo, paymentRepo, eventRepo, settingsRepo);

    private HrEmployee person(String number, String first, String account, String branch) {
        HrEmployee e = HrEmployee.create(tenant, number, first, "Nkosi", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        e.setBankName("FNB");
        e.setBankAccountNumber(account);
        e.setBankBranchCode(branch);
        emps.put(e.getId(), e);
        return e;
    }

    private HrPayslip pay(HrEmployee e, String net) {
        HrPayslip s = HrPayslip.create(tenant, run.getId(), e.getId(), new BigDecimal(net), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        s.applyCalculations(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(net), BigDecimal.ZERO, BigDecimal.ZERO, 2027);
        slips.add(s);
        return s;
    }

    private void finalised() { run.finalise(UUID.randomUUID()); }

    @Test
    void makesUpOnePaymentPerPayslipAndListsWhoWasLeftOut() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "21000.50");
        pay(person("E2", "Sipho", null, null), "15000");
        pay(person("E3", "Lerato", "1234567890", "250655"), "0");
        Summary s = service.makeUp(tenant, run.getId(), actor);
        assertEquals(1, s.count());
        assertEquals(new BigDecimal("21000.50"), s.total());
        assertEquals(2, s.skipped().size());
        assertTrue(s.skipped().stream().anyMatch(x -> x.reason().startsWith("Bank details: no bank account")));
        assertTrue(s.skipped().stream().anyMatch(x -> x.reason().contains("zero")));
        assertEquals("SAL PR00005 E1", payments.get(0).getReference());
        assertEquals("PAYMENTS_MADE_UP:1 payments, R21000.50, 2 left out", events.get(0));
    }

    @Test
    void refusesUntilTheRunIsFinalised() {
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        assertThrows(IllegalStateException.class, () -> service.makeUp(tenant, run.getId(), actor));
        assertTrue(payments.isEmpty());
    }

    @Test
    void makingUpAgainReplacesPendingPaymentsInsteadOfDoubling() {
        finalised();
        HrEmployee e = person("E1", "Thandi", "1234567890", "250655");
        pay(e, "100");
        service.makeUp(tenant, run.getId(), actor);
        e.setBankAccountNumber("5555 666 777");
        service.makeUp(tenant, run.getId(), actor);
        assertEquals(1, payments.size());
        assertEquals("5555666777", payments.get(0).getAccountNumber());
    }

    @Test
    void theFileHoldsPendingPaymentsAndIsRecorded() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        service.makeUp(tenant, run.getId(), actor);
        FileResult f = service.file(tenant, run.getId(), actor);
        assertEquals("salary-payments-PR00005.csv", f.filename());
        assertTrue(f.csv().contains("Thandi Nkosi,FNB,250655,1234567890,100.00,SAL PR00005 E1"));
        assertTrue(events.stream().anyMatch(x -> x.startsWith("PAYMENT_FILE_DOWNLOADED")));
        assertTrue(payments.get(0).getExportedAt() != null);
    }

    @Test
    void thereIsNoFileBeforePaymentsAreMadeUp() {
        finalised();
        assertThrows(IllegalStateException.class, () -> service.file(tenant, run.getId(), actor));
    }

    @Test
    void cannotConfirmPaidBeforeTheFileWasDownloaded() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        service.makeUp(tenant, run.getId(), actor);
        assertThrows(IllegalStateException.class, () -> service.confirmPaid(tenant, run.getId(), actor, null));
        service.file(tenant, run.getId(), actor);
        Summary s = service.confirmPaid(tenant, run.getId(), actor, "Uploaded to the bank");
        assertEquals(1, s.paid());
        assertEquals(0, s.pending());
        assertEquals("PAID", payments.get(0).getStatus());
        assertTrue(events.stream().anyMatch(x -> x.startsWith("PAYMENTS_CONFIRMED_PAID:1 payments. Uploaded")));
    }

    @Test
    void oncePaidThePaymentsCannotBeMadeUpAgain() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        service.makeUp(tenant, run.getId(), actor);
        service.file(tenant, run.getId(), actor);
        service.confirmPaid(tenant, run.getId(), actor, null);
        assertThrows(IllegalStateException.class, () -> service.makeUp(tenant, run.getId(), actor));
        assertEquals(1, payments.size());
    }

    @Test
    void aReturnedPaymentKeepsItsReasonAndShowsInTheTotals() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        pay(person("E2", "Sipho", "9876543210", "632005"), "200");
        service.makeUp(tenant, run.getId(), actor);
        service.file(tenant, run.getId(), actor);
        service.confirmPaid(tenant, run.getId(), actor, null);
        UUID id = payments.stream().filter(p -> p.getBeneficiaryName().startsWith("Sipho")).findFirst().orElseThrow().getId();
        Summary s = service.markReturned(tenant, id, "Account closed", actor);
        assertEquals(1, s.returned());
        assertEquals(new BigDecimal("200.00"), s.returnedTotal());
        assertEquals(1, s.paid());
        assertFalse(s.canMakeUp());
        assertThrows(IllegalArgumentException.class, () -> service.markReturned(tenant, id, "Again", actor));
        assertThrows(IllegalArgumentException.class, () -> service.markReturned(tenant, payments.get(0).getId(), " ", actor));
    }

    @Test
    void accountNumbersAreMaskedOnScreen() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "250655"), "100");
        service.makeUp(tenant, run.getId(), actor);
        Summary s = service.summary(tenant, run.getId());
        assertEquals("••••7890", s.payments().get(0).accountMasked());
        assertFalse(s.payments().get(0).toString().contains("1234567890"));
    }

    @Test
    void everyStepNeedsAKnownUserAndAKnownRun() {
        finalised();
        assertThrows(AccessDeniedException.class, () -> service.makeUp(tenant, run.getId(), null));
        assertThrows(AccessDeniedException.class, () -> service.file(tenant, run.getId(), null));
        assertThrows(ResourceNotFoundException.class, () -> service.summary(tenant, UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.markReturned(tenant, UUID.randomUUID(), "x", actor));
    }

    @Test
    void aLeaverOnTheRunIsStillPaid() {
        finalised();
        HrEmployee leaver = person("E9", "Zanele", "1234567890", "250655");
        pay(leaver, "5000");
        assertEquals(1, service.makeUp(tenant, run.getId(), actor).count());
    }

    // ── Bank layouts and the second-person check ──────────────────────────

    private void companyAccount(boolean secondCheck) {
        settings = HrPayrollSettings.create(tenant.getValue());
        settings.setBankFile(secondCheck, "250655", "62011234567", actor);
    }

    @Test
    void aBankServFileHasTheHeadersOneTransactionPerPaymentTheContraAndTheTrailers() {
        finalised();
        companyAccount(false);
        pay(person("E1", "Thandi", "1234567890", "632005"), "3728.68");
        pay(person("E2", "Sipho", "9876543210", "250655"), "100");
        service.makeUp(tenant, run.getId(), actor);

        FileResult f = service.file(tenant, run.getId(), actor, "BANKSERV_ACB");

        String[] rows = f.csv().split("\r\n");
        assertEquals(7, rows.length);                       // 02, 04, two payments, contra, 92, 94
        for (String r : rows) assertEquals(180, r.length());
        assertEquals("02", rows[0].substring(0, 2));
        assertEquals("04", rows[1].substring(0, 2));
        assertEquals("10", rows[2].substring(0, 2));
        assertEquals("12", rows[4].substring(0, 2));
        assertEquals("92", rows[5].substring(0, 2));
        assertEquals("94", rows[6].substring(0, 2));
        assertEquals("salary-payments-PR00005.txt", f.filename());
        assertEquals("BANKSERV_ACB", f.layout());
        assertTrue(payments.get(0).getExportedAt() != null);
        assertEquals(actor, payments.get(0).getExportedBy());
        assertTrue(events.stream().anyMatch(x -> x.startsWith("PAYMENT_FILE_DOWNLOADED") && x.contains("BANKSERV_ACB")));
    }

    @Test
    void aBankServFileNeedsTheCompanysOwnAccountAndNothingIsMarkedDownloadedWithout() {
        finalised();
        pay(person("E1", "Thandi", "1234567890", "632005"), "100");
        service.makeUp(tenant, run.getId(), actor);
        assertThrows(IllegalStateException.class, () -> service.file(tenant, run.getId(), actor, "BANKSERV_ACB"));
        assertNull(payments.get(0).getExportedAt());
        assertThrows(IllegalStateException.class, () -> service.file(tenant, run.getId(), actor, "NOPE"));
    }

    @Test
    void withTheSecondPersonCheckOnTheOneWhoDownloadedCannotConfirm() {
        finalised();
        companyAccount(true);
        pay(person("E1", "Thandi", "1234567890", "632005"), "100");
        service.makeUp(tenant, run.getId(), actor);
        service.file(tenant, run.getId(), actor);
        assertThrows(IllegalStateException.class, () -> service.confirmPaid(tenant, run.getId(), actor, null));
        assertEquals("PENDING", payments.get(0).getStatus());
        service.confirmPaid(tenant, run.getId(), UUID.randomUUID(), null);
        assertEquals("PAID", payments.get(0).getStatus());
    }

    @Test
    void withTheCheckOffTheSamePersonMayConfirm() {
        finalised();
        companyAccount(false);
        pay(person("E1", "Thandi", "1234567890", "632005"), "100");
        service.makeUp(tenant, run.getId(), actor);
        service.file(tenant, run.getId(), actor);
        service.confirmPaid(tenant, run.getId(), actor, null);
        assertEquals("PAID", payments.get(0).getStatus());
    }
}
