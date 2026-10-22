package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.hr.domain.model.LetterRules;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.LetterHarness.*;
import static za.co.handyflow.platform.hr.dto.LetterDtos.*;

/** Telling the employee a letter is waiting, and making one letter for many people. */
class LetterBulkAndNoticeTest {

    private final LetterHarness h = new LetterHarness();
    private final UUID ann = h.employee("Ann", "Adams");
    private final UUID ben = h.employee("Ben", "Brown");
    private final UUID cara = h.employee("Cara", "Cole");

    private UUID plainTemplate() {
        return h.service.createTemplate(TENANT, new TemplateRequest("Policy update", "GENERAL", "Policy update for {{employee.firstName}}",
                "Dear {{employee.firstName}}, the leave policy of {{company.name}} changes on {{today}}.", false, true), HR_USER, ISSUER).id();
    }

    private HrEmployee person(UUID id) { return h.employees.findActiveById(TENANT, id).orElseThrow(); }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(h.notifications, atLeast(0)).send(c.capture());
        return c.getAllValues();
    }

    // ── Telling the employee ────────────────────────────────────────────────────────────────────

    @Test
    void anIssuedLetterTellsTheEmployeeItsReferenceButNotItsSubject() {
        when(person(ann).getEmail()).thenReturn("ann@example.com");
        UUID letter = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, ISSUER).items().get(0).letterId();
        Detail issued = h.service.issue(TENANT, letter, HR_USER, ISSUER);
        NotificationRequest n = sent().stream().filter(x -> x.type() == NotificationType.LETTER_ISSUED).findFirst().orElseThrow();
        assertThat(n.recipients().stream().map(Recipient::email).toList()).containsExactly("ann@example.com");
        assertThat(n.message()).contains(issued.row().number());
        assertThat(n.message()).doesNotContain("Policy update").doesNotContain("leave policy");
        assertThat(n.title()).doesNotContain("Policy");
    }

    @Test
    void anEmployeeWithoutAnEmailIsSimplyNotTold() {
        UUID letter = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, ISSUER).items().get(0).letterId();
        assertThat(h.service.issue(TENANT, letter, HR_USER, ISSUER).row().status()).isEqualTo(LetterRules.ISSUED);
        assertThat(sent()).isEmpty();
    }

    @Test
    void aMessageThatCannotBeSentNeverUndoesTheIssue() {
        when(person(ann).getEmail()).thenReturn("ann@example.com");
        doThrow(new IllegalStateException("mail down")).when(h.notifications).send(any());
        UUID letter = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, ISSUER).items().get(0).letterId();
        assertThat(h.service.issue(TENANT, letter, HR_USER, ISSUER).row().status()).isEqualTo(LetterRules.ISSUED);
    }

    // ── Many at once ────────────────────────────────────────────────────────────────────────────

    @Test
    void oneTemplateBecomesOneDraftPerEmployeeEachWithItsOwnNumberAndName() {
        BulkPrepared r = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann, ben, cara, ann)), HR_USER, ISSUER);
        assertThat(r.made()).isEqualTo(3);
        assertThat(r.refused()).isZero();
        assertThat(r.items().stream().map(BulkItem::number).distinct().count()).isEqualTo(3);
        assertThat(h.savedLetters).hasSize(3);
        assertThat(h.savedLetters.stream().allMatch(l -> LetterRules.DRAFT.equals(l.getStatus()))).isTrue();
        assertThat(h.savedLetters.stream().map(HrLetter::getBody).anyMatch(b -> b.startsWith("Dear Ben,"))).isTrue();
    }

    @Test
    void aTemplateAboutACaseIsRefusedBeforeAnythingIsMade() {
        UUID warning = h.service.templates(TENANT, READER).templates().stream().filter(t -> t.code().equals("WARNING_LETTER")).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> h.service.prepareBulk(TENANT, new BulkPrepareRequest(warning, List.of(ann)), HR_USER, ISSUER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("case");
        assertThat(h.savedLetters).isEmpty();
    }

    @Test
    void youCannotMakeALetterAboutYourselfAndTheOthersStillGoAhead() {
        UUID hrUser = h.signedInAs(ann);
        BulkPrepared r = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann, ben)), hrUser, ISSUER);
        assertThat(r.made()).isEqualTo(1);
        assertThat(r.refused()).isEqualTo(1);
        BulkItem refused = r.items().stream().filter(i -> i.letterId() == null).findFirst().orElseThrow();
        assertThat(refused.employeeId()).isEqualTo(ann);
        assertThat(refused.problem()).contains("yourself");
    }

    @Test
    void anUnknownEmployeeIsReportedAndTheRestGoAhead() {
        UUID ghost = UUID.randomUUID();
        BulkPrepared r = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ghost, ben)), HR_USER, ISSUER);
        assertThat(r.made()).isEqualTo(1);
        assertThat(r.items().get(0).problem()).isNotNull();
    }

    @Test
    void bulkNeedsThePermissionToIssue() {
        assertThatThrownBy(() -> h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, READER))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> h.service.issueBulk(TENANT, new BulkIssueRequest(List.of(UUID.randomUUID())), HR_USER, READER))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void issuingManyIssuesTheCompleteOnesAndReportsTheOthers() {
        UUID withGap = h.service.createTemplate(TENANT, new TemplateRequest("Needs a figure", "GENERAL", "Pay for {{employee.firstName}}",
                "Your pay is {{manual.remuneration}}.", false, true), HR_USER, ISSUER).id();
        UUID good = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, ISSUER).items().get(0).letterId();
        BulkItem gappy = h.service.prepareBulk(TENANT, new BulkPrepareRequest(withGap, List.of(ben)), HR_USER, ISSUER).items().get(0);
        assertThat(gappy.gaps()).isNotEmpty();

        BulkIssued r = h.service.issueBulk(TENANT, new BulkIssueRequest(List.of(good, gappy.letterId(), good)), HR_USER, ISSUER);
        assertThat(r.issued()).isEqualTo(1);
        assertThat(r.refused()).isEqualTo(1);
        BulkIssueItem refused = r.items().stream().filter(i -> i.problem() != null).findFirst().orElseThrow();
        assertThat(refused.letterId()).isEqualTo(gappy.letterId());
        assertThat(refused.problem()).contains("gaps");
        assertThat(h.savedLetters.stream().filter(l -> LetterRules.ISSUED.equals(l.getStatus())).count()).isEqualTo(1);
        assertThat(h.savedLetters.stream().filter(l -> LetterRules.DRAFT.equals(l.getStatus())).count()).isEqualTo(1);
    }

    @Test
    void aLetterThatIsAlreadyIssuedIsReportedNotIssuedTwice() {
        UUID letter = h.service.prepareBulk(TENANT, new BulkPrepareRequest(plainTemplate(), List.of(ann)), HR_USER, ISSUER).items().get(0).letterId();
        h.service.issue(TENANT, letter, HR_USER, ISSUER);
        BulkIssued r = h.service.issueBulk(TENANT, new BulkIssueRequest(List.of(letter)), HR_USER, ISSUER);
        assertThat(r.issued()).isZero();
        assertThat(r.items().get(0).problem()).contains("draft");
    }

    @Test
    void theNumberToIssueIsBounded() {
        List<UUID> many = new java.util.ArrayList<>();
        for (int i = 0; i <= LetterRules.BULK_ISSUE_MAX; i++) many.add(UUID.randomUUID());
        assertThatThrownBy(() -> h.service.issueBulk(TENANT, new BulkIssueRequest(many), HR_USER, ISSUER)).isInstanceOf(IllegalArgumentException.class);
    }
}
