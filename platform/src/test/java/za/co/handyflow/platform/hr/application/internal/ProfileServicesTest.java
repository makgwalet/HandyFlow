package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrCareerEvent;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeRelative;
import za.co.handyflow.platform.hr.domain.repository.HrCareerEventRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRelativeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.ProfileDtos.CareerRequest;
import za.co.handyflow.platform.hr.dto.ProfileDtos.CareerView;
import za.co.handyflow.platform.hr.dto.ProfileDtos.RelativeRequest;
import za.co.handyflow.platform.hr.dto.ProfileDtos.RelativeView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Next of kin, dependants and the career timeline, against in-memory stand-ins for the repositories. */
class ProfileServicesTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final HrEmployee emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 3, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");

    private final List<HrEmployeeRelative> relativeRows = new ArrayList<>();
    private final List<HrCareerEvent> careerRows = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();

    private boolean linked = true;

    private final HrEmployeeRepository employees = (HrEmployeeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrEmployeeRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "findActiveById" -> a[1].equals(emp.getId()) ? Optional.of(emp) : Optional.empty();
                case "findByUserId" -> linked && a[1].equals(userId) ? Optional.of(emp) : Optional.empty();
                default -> throw new UnsupportedOperationException(m.getName());
            });

    private final HrEmployeeRelativeRepository relativeRepo = (HrEmployeeRelativeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrEmployeeRelativeRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "forEmployee" -> relativeRows.stream().filter(r -> r.getRemovedAt() == null && r.getEmployeeId().equals(a[1])).toList();
                case "findOne" -> relativeRows.stream().filter(r -> r.getRemovedAt() == null && r.getEmployeeId().equals(a[1]) && r.getId().equals(a[2])).findFirst();
                case "save" -> { if (!relativeRows.contains(a[0])) relativeRows.add((HrEmployeeRelative) a[0]); yield a[0]; }
                default -> throw new UnsupportedOperationException(m.getName());
            });

    private final HrCareerEventRepository careerRepo = (HrCareerEventRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrCareerEventRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "forEmployee" -> careerRows.stream().filter(r -> r.getRemovedAt() == null && r.getEmployeeId().equals(a[1]))
                        .sorted(Comparator.comparing(HrCareerEvent::getEventDate).reversed()).toList();   // the query orders newest first
                case "findOne" -> careerRows.stream().filter(r -> r.getRemovedAt() == null && r.getId().equals(a[1])).findFirst();
                case "save" -> { if (!careerRows.contains(a[0])) careerRows.add((HrCareerEvent) a[0]); yield a[0]; }
                default -> throw new UnsupportedOperationException(m.getName());
            });

    private final EmployeeAuditService audit = new EmployeeAuditService(null) {
        @Override public void record(TenantId t, UUID employeeId, String action, String detail, UUID by) { audited.add(action + (detail == null ? "" : ":" + detail)); }
    };

    private final RelativeService relatives = new RelativeService(relativeRepo, employees, audit);
    private final CareerService career = new CareerService(careerRepo, employees, audit);

    private static RelativeRequest kin(String name, String phone) {
        return new RelativeRequest("NEXT_OF_KIN", name, "SPOUSE", phone, null, null, null, false, null, null);
    }

    private static RelativeRequest child(String name, LocalDate dob, Boolean beneficiary, String percent) {
        return new RelativeRequest("DEPENDANT", name, "CHILD", null, null, dob, null, beneficiary, percent == null ? null : new BigDecimal(percent), null);
    }

    @Test
    void addsAndListsAFamilyMember() {
        RelativeView v = relatives.add(tenant, emp.getId(), kin("Thandi Dlamini", "082 555 0101"), actor);
        assertThat(v.fullName()).isEqualTo("Thandi Dlamini");
        assertThat(v.relationshipLabel()).isEqualTo("Spouse");
        assertThat(relatives.forEmployee(tenant, emp.getId())).hasSize(1);
        assertThat(audited).containsExactly("RELATIVE_ADDED:next of kin");
    }

    @Test
    void theAuditTrailNeverHoldsTheirDetails() {
        relatives.add(tenant, emp.getId(), kin("Thandi Dlamini", "082 555 0101"), actor);
        assertThat(String.join(" ", audited)).doesNotContain("Thandi").doesNotContain("082");
    }

    @Test
    void refusesWhatTheRulesRefuse() {
        assertThatThrownBy(() -> relatives.add(tenant, emp.getId(), kin("Thandi", null), actor)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("phone");
        assertThat(relativeRows).isEmpty();
        assertThat(audited).isEmpty();
    }

    @Test
    void sharesAcrossAllBeneficiariesStayWithinAHundred() {
        LocalDate dob = LocalDate.of(2020, 1, 1);
        relatives.add(tenant, emp.getId(), child("A", dob, true, "60"), actor);
        relatives.add(tenant, emp.getId(), child("B", dob, true, "40"), actor);
        assertThatThrownBy(() -> relatives.add(tenant, emp.getId(), child("C", dob, true, "1"), actor)).hasMessageContaining("101%");
        assertThat(relatives.forEmployee(tenant, emp.getId())).hasSize(2);
    }

    @Test
    void changingALineKeepsItsOwnShareOutOfTheTotal() {
        LocalDate dob = LocalDate.of(2020, 1, 1);
        RelativeView a = relatives.add(tenant, emp.getId(), child("A", dob, true, "60"), actor);
        RelativeView updated = relatives.update(tenant, emp.getId(), a.id(), child("A", dob, true, "100"), actor);
        assertThat(updated.beneficiaryPercent()).isEqualByComparingTo("100");
        assertThat(audited).contains("RELATIVE_CHANGED:dependant");
    }

    @Test
    void removingKeepsTheRowButHidesIt() {
        RelativeView v = relatives.add(tenant, emp.getId(), kin("Thandi", "0825550101"), actor);
        relatives.remove(tenant, emp.getId(), v.id(), actor);
        assertThat(relativeRows).hasSize(1);
        assertThat(relativeRows.get(0).getRemovedBy()).isEqualTo(actor);
        assertThat(relatives.forEmployee(tenant, emp.getId())).isEmpty();
        assertThatThrownBy(() -> relatives.remove(tenant, emp.getId(), v.id(), actor)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aLineOfAnotherEmployeeIsNotFound() {
        RelativeView v = relatives.add(tenant, emp.getId(), kin("Thandi", "0825550101"), actor);
        assertThatThrownBy(() -> relatives.update(tenant, UUID.randomUUID(), v.id(), kin("X", "0825550101"), actor)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        assertThatThrownBy(() -> relatives.forEmployee(tenant, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnidentifiedUserCannotWrite() {
        assertThatThrownBy(() -> relatives.add(tenant, emp.getId(), kin("Thandi", "0825550101"), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anEmployeeIsFoundFromTheirLoginOnly() {
        assertThat(relatives.employeeOf(tenant, userId)).isEqualTo(Optional.of(emp.getId()));
        assertThat(relatives.employeeOf(tenant, UUID.randomUUID())).isEqualTo(Optional.empty());
        assertThat(relatives.employeeOf(tenant, null)).isEqualTo(Optional.empty());
        linked = false;
        assertThat(relatives.employeeOf(tenant, userId)).isEqualTo(Optional.empty());
    }

    // ── career ──

    @Test
    void anEarlierJobIsAddedByHandAndShownNewestFirst() {
        career.add(tenant, emp.getId(), new CareerRequest("PRIOR_EMPLOYMENT", LocalDate.of(2018, 1, 1), LocalDate.of(2023, 12, 31), "Acme", "Clerk", null, null), actor);
        career.add(tenant, emp.getId(), new CareerRequest("PROMOTION", LocalDate.of(2025, 6, 1), null, null, "Senior Clerk", null, null), actor);
        List<CareerView> list = career.forEmployee(tenant, emp.getId(), false);
        assertThat(list).extracting(CareerView::kind).containsExactly("PROMOTION", "PRIOR_EMPLOYMENT");
        assertThat(list).allMatch(v -> !v.auto());
        assertThat(audited).contains("CAREER_ADDED:Promotion", "CAREER_ADDED:Earlier employer");
    }

    @Test
    void aBadEntryIsRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> career.add(tenant, emp.getId(), new CareerRequest("SALARY_CHANGED", LocalDate.of(2025, 6, 1), null, null, null, null, "x"), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(careerRows).isEmpty();
    }

    @Test
    void salaryLinesAreHiddenFromThoseWhoMayNotSeePayAndShowAPercentage() {
        careerRows.add(HrCareerEvent.of(tenant.getValue(), emp.getId(), "SALARY_CHANGED", LocalDate.of(2025, 3, 1), null, null, null, null, "30000", "32500", null, true, true, actor));
        careerRows.add(HrCareerEvent.of(tenant.getValue(), emp.getId(), "HIRED", LocalDate.of(2024, 3, 1), null, null, "Clerk", null, null, "Clerk", null, false, true, null));
        assertThat(career.forEmployee(tenant, emp.getId(), false)).extracting(CareerView::kind).containsExactly("HIRED");
        List<CareerView> withPay = career.forEmployee(tenant, emp.getId(), true);
        assertThat(withPay).extracting(CareerView::kind).containsExactly("SALARY_CHANGED", "HIRED");
        assertThat(withPay.get(0).percentChange()).isEqualByComparingTo("8.3");
        assertThat(withPay.get(1).percentChange()).isNull();
    }

    @Test
    void onlyAHandEntryCanBeRemoved() {
        HrCareerEvent auto = HrCareerEvent.of(tenant.getValue(), emp.getId(), "HIRED", LocalDate.of(2024, 3, 1), null, null, null, null, null, null, null, false, true, null);
        careerRows.add(auto);
        assertThatThrownBy(() -> career.remove(tenant, auto.getId(), actor)).isInstanceOf(IllegalStateException.class).hasMessageContaining("system");
        CareerView hand = career.add(tenant, emp.getId(), new CareerRequest("OTHER", LocalDate.of(2025, 6, 1), null, null, null, null, "Acting manager"), actor);
        career.remove(tenant, hand.id(), actor);
        assertThat(career.forEmployee(tenant, emp.getId(), true)).hasSize(1);
        assertThat(audited).contains("CAREER_REMOVED:Other");
    }

    @Test
    void theRecorderSavesAChangeAsASystemLine() {
        new CareerRecorder(careerRepo).on(CareerChange.of(tenant, emp.getId(), "TITLE_CHANGED", LocalDate.of(2026, 10, 28), "Clerk", "Senior Clerk", null, actor));
        assertThat(careerRows).hasSize(1);
        HrCareerEvent e = careerRows.get(0);
        assertThat(e.isAuto()).isTrue();
        assertThat(e.getFromValue()).isEqualTo("Clerk");
        assertThat(e.getToValue()).isEqualTo("Senior Clerk");
        assertThat(e.isPayRelated()).isFalse();
    }

    @Test
    void theRecorderCutsOverlongText() {
        new CareerRecorder(careerRepo).on(new CareerChange(tenant, emp.getId(), "TERMINATED", LocalDate.of(2026, 10, 28), null, null, null, null, "x".repeat(700), false, actor));
        assertThat(careerRows.get(0).getNote()).hasSize(500);
    }
}
