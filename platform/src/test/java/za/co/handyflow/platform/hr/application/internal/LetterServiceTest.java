package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.hr.domain.model.HrLetterTemplate;
import za.co.handyflow.platform.hr.domain.model.LetterDefaults;
import za.co.handyflow.platform.hr.dto.LetterDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.handyflow.platform.hr.application.internal.LetterHarness.*;

class LetterServiceTest {

    private final LetterHarness h = new LetterHarness();

    /** Someone who is not the officer who owns the cases the harness opens. */
    private static final UUID OTHER_OFFICER = UUID.fromString("00000000-0000-0000-0000-0000000000c9");

    private UUID template(String code) {
        h.service.templates(TENANT, READER);
        return h.savedTemplates.stream().filter(t -> t.getCode().equals(code)).findFirst().orElseThrow().getId();
    }

    private Detail draftFor(UUID employee, String code) {
        return h.service.prepare(TENANT, new PrepareRequest(template(code), employee, null), HR_USER, ISSUER);
    }

    // ── Templates ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aCompanyGetsTheStandardTemplatesTheFirstTimeItLooks() {
        Templates t = h.service.templates(TENANT, READER);
        assertThat(t.templates().size()).isEqualTo(LetterDefaults.ALL.size());
        assertThat(t.fields().size() > 20).isTrue();
        h.service.templates(TENANT, READER);
        assertThat(h.savedTemplates.size()).isEqualTo(LetterDefaults.ALL.size());
    }

    @Test
    void templatesNeedPermission() {
        assertThatThrownBy(() -> h.service.templates(TENANT, NOTHING)).isInstanceOf(AccessDeniedException.class);
        UUID id = template("GENERAL_LETTER");
        TemplateRequest req = new TemplateRequest("General", "GENERAL", "S", "Dear {{employee.firstName}}", false, true);
        assertThatThrownBy(() -> h.service.saveTemplate(TENANT, id, req, HR_USER, READER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.service.createTemplate(TENANT, req, HR_USER, READER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void savingATemplateRaisesItsRevisionAndKeepsAStandardCategory() {
        UUID id = template("GENERAL_LETTER");
        int before = h.savedTemplates.get(0).getRevision();
        TemplateView v = h.service.saveTemplate(TENANT, id, new TemplateRequest("Our letter", "DISCIPLINARY", "Hello {{employee.name}}", "Dear {{employee.firstName}}", true, true),
                HR_USER, ISSUER);
        assertThat(v.name()).isEqualTo("Our letter");
        assertThat(v.category()).isEqualTo("GENERAL");
        assertThat(v.customised()).isTrue();
        assertThat(v.standard()).isTrue();
        assertThat(v.revision()).isEqualTo(before + 1);
        assertThat(v.requiresAck()).isTrue();
    }

    @Test
    void aTemplateWithAnUnknownFieldIsRefused() {
        UUID id = template("GENERAL_LETTER");
        assertThatThrownBy(() -> h.service.saveTemplate(TENANT, id, new TemplateRequest("n", "GENERAL", "s", "Your pay is {{employee.salary}}", false, true), HR_USER, ISSUER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("employee.salary");
    }

    @Test
    void aCompanyCanWriteItsOwnTemplateAndRestoreAStandardOne() {
        TemplateView mine = h.service.createTemplate(TENANT, new TemplateRequest("Letter of good standing", "EMPLOYMENT", "Good standing", "{{employee.name}} is in good standing.", false, true),
                HR_USER, ISSUER);
        assertThat(mine.code()).isEqualTo("LETTER_OF_GOOD_STANDING");
        assertThat(mine.standard()).isFalse();
        assertThatThrownBy(() -> h.service.restoreTemplate(TENANT, mine.id(), HR_USER, ISSUER)).isInstanceOf(IllegalStateException.class);

        UUID std = template("GENERAL_LETTER");
        h.service.saveTemplate(TENANT, std, new TemplateRequest("Changed", "GENERAL", "Changed", "Changed wording", false, true), HR_USER, ISSUER);
        TemplateView back = h.service.restoreTemplate(TENANT, std, HR_USER, ISSUER);
        assertThat(back.customised()).isFalse();
        assertThat(back.name()).isEqualTo("General letter");
        assertThat(back.body().contains("{{manual.details}}")).isTrue();
    }

    // ── Preparing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aDraftIsFilledInAndGapsAreMarked() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "EMPLOYMENT_CONFIRMATION");
        assertThat(d.row().status()).isEqualTo("DRAFT");
        assertThat(d.row().number()).isEqualTo("LT-" + today().getYear() + "-0007");
        assertThat(d.row().subject()).isEqualTo("Confirmation of employment: Thandi Mokoena");
        assertThat(d.body().contains("Thandi Mokoena (employee number E-")).isTrue();
        assertThat(d.body().contains("since 1 March 2024 as Clerk")).isTrue();
        assertThat(d.gaps()).isEmpty();
        assertThat(d.actions()).containsExactly("EDIT", "ISSUE", "VOID");
        assertThat(d.events().size()).isEqualTo(1);
    }

    @Test
    void thingsOnlyAPersonCanTypeAreLeftAsGaps() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "CERTIFICATE_OF_SERVICE");
        assertThat(d.gaps()).contains("Employer's address (type in)", "Remuneration (type in)", "Last day of employment");
        assertThat(d.body().contains("[[Remuneration (type in)]]")).isTrue();
    }

    @Test
    void aCaseLetterUsesTheFactsOnTheCase() {
        UUID emp = h.employee("Thandi", "Mokoena");
        HrErCase c = h.discipline(emp, false);
        Detail d = h.service.prepare(TENANT, new PrepareRequest(template("NOTICE_OF_HEARING"), null, c.getId()), HR_USER, ISSUER);
        assertThat(d.row().caseId()).isEqualTo(c.getId());
        assertThat(d.row().employeeId()).isEqualTo(emp);
        assertThat(d.row().caseNumber()).isEqualTo("ER-2026-0001");
        assertThat(d.row().subject()).isEqualTo("Notice of disciplinary hearing: case ER-2026-0001");
        assertThat(d.body().contains("at 09:00 at Boardroom")).isTrue();
        assertThat(d.body().contains("at least 48 hours")).isTrue();
        assertThat(d.row().requiresAck()).isTrue();
    }

    @Test
    void theAppealDeadlineRunsFromTheDayTheLetterIsMadeUntilTheEmployeeIsTold() {
        UUID emp = h.employee("Thandi", "Mokoena");
        HrErCase c = h.discipline(emp, false);
        c.decide("WRITTEN_WARNING", today(), "Pattern of lateness.", HR_USER, 6, null);
        Detail d = h.service.prepare(TENANT, new PrepareRequest(template("WARNING_LETTER"), null, c.getId()), HR_USER, ISSUER);
        assertThat(d.body().contains("Written warning")).isTrue();
        assertThat(d.body().contains("stays valid for 6 months")).isTrue();
        String seven = za.co.handyflow.platform.hr.domain.model.LetterRules.longDate(today().plusDays(7));
        assertThat(d.body().contains("by " + seven)).isTrue();
    }

    @Test
    void aLetterForTheWrongEmployeeOnACaseIsRefused() {
        UUID emp = h.employee("Thandi", "Mokoena");
        UUID other = h.employee("Sipho", "Nkosi");
        HrErCase c = h.discipline(emp, false);
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(template("NOTICE_OF_HEARING"), other, c.getId()), HR_USER, ISSUER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different employee");
    }

    @Test
    void nobodyPreparesALetterAboutThemselves() {
        UUID emp = h.employee("Thandi", "Mokoena");
        UUID me = h.signedInAs(emp);
        UUID id = template("GENERAL_LETTER");
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, emp, null), me, ISSUER)).isInstanceOf(AccessDeniedException.class);
        HrErCase c = h.discipline(emp, false);
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, null, c.getId()), me, ISSUER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aConfidentialCaseStaysHiddenFromPeopleWhoMayNotSeeIt() {
        UUID emp = h.employee("Thandi", "Mokoena");
        HrErCase c = h.discipline(emp, true);
        UUID id = template("NOTICE_OF_HEARING");
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, null, c.getId()), OTHER_OFFICER, ISSUER)).isInstanceOf(ResourceNotFoundException.class);
        Detail d = h.service.prepare(TENANT, new PrepareRequest(id, null, c.getId()), OTHER_OFFICER, java.util.Set.of("HR_LETTER_ISSUE", "HR_ER_READ", "HR_SENSITIVE_READ"));
        assertThat(d.row().caseId()).isEqualTo(c.getId());
        Detail owned = h.service.prepare(TENANT, new PrepareRequest(id, null, c.getId()), HR_USER, ISSUER);
        assertThat(owned.row().caseId()).isEqualTo(c.getId());
    }

    @Test
    void preparingNeedsPermissionAnActiveTemplateAndAnEmployee() {
        UUID emp = h.employee("Thandi", "Mokoena");
        UUID id = template("GENERAL_LETTER");
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, emp, null), HR_USER, READER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(null, emp, null), HR_USER, ISSUER)).hasMessageContaining("Choose the template");
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, null, null), HR_USER, ISSUER)).hasMessageContaining("Choose the employee");
        h.service.saveTemplate(TENANT, id, new TemplateRequest("General letter", "GENERAL", "S", "Dear {{employee.firstName}}", false, false), HR_USER, ISSUER);
        assertThatThrownBy(() -> h.service.prepare(TENANT, new PrepareRequest(id, emp, null), HR_USER, ISSUER)).hasMessageContaining("switched off");
    }

    // ── Editing, issuing, voiding ─────────────────────────────────────────────────────────────────

    @Test
    void aDraftCanBeEditedButNotWithMergeFields() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "GENERAL_LETTER");
        Detail e = h.service.updateDraft(TENANT, d.row().id(), new DraftRequest("Hello", "Dear Thandi\n\nHere is the news."), HR_USER, ISSUER);
        assertThat(e.row().subject()).isEqualTo("Hello");
        assertThat(e.gaps()).isEmpty();
        assertThatThrownBy(() -> h.service.updateDraft(TENANT, d.row().id(), new DraftRequest("Hello", "Dear {{employee.name}}"), HR_USER, ISSUER))
                .hasMessageContaining("merge fields");
    }

    @Test
    void aLetterWithGapsCannotBeIssued() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "GENERAL_LETTER");
        assertThatThrownBy(() -> h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER)).hasMessageContaining("Fill in the gaps").hasMessageContaining("Details (type in)");
        assertThat(h.files.stored.isEmpty()).isTrue();
    }

    @Test
    void issuingKeepsThePdfAndFixesTheWording() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = h.service.updateDraft(TENANT, draftFor(emp, "GENERAL_LETTER").row().id(), new DraftRequest("Hello", "Dear Thandi\n\nThe news."), HR_USER, ISSUER);
        Detail i = h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        assertThat(i.row().status()).isEqualTo("ISSUED");
        assertThat(i.row().issuedOn()).isEqualTo(today());
        assertThat(i.fileName()).isEqualTo(i.row().number() + ".pdf");
        assertThat(i.fileSha256().length()).isEqualTo(64);
        assertThat(h.files.stored.size()).isEqualTo(1);
        assertThat(i.actions()).containsExactly("DOWNLOAD", "VOID");
        assertThatThrownBy(() -> h.service.updateDraft(TENANT, d.row().id(), new DraftRequest("Other", "Other"), HR_USER, ISSUER)).hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER)).hasMessageContaining("Only a draft");
    }

    @Test
    void ifTheFileCannotBeSavedTheLetterIsNotIssued() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = h.service.updateDraft(TENANT, draftFor(emp, "GENERAL_LETTER").row().id(), new DraftRequest("Hello", "Dear Thandi"), HR_USER, ISSUER);
        h.files.failToStore = true;
        assertThatThrownBy(() -> h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not issued");
        assertThat(h.savedLetters.get(0).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void issuingACaseLetterShowsOnTheCaseTimelineForTheEmployee() {
        UUID emp = h.employee("Thandi", "Mokoena");
        HrErCase c = h.discipline(emp, false);
        Detail d = h.service.prepare(TENANT, new PrepareRequest(template("NOTICE_OF_HEARING"), null, c.getId()), HR_USER, ISSUER);
        assertThat(d.gaps().toString()).contains("Initiator").contains("Charges");
        h.service.updateDraft(TENANT, d.row().id(), new DraftRequest(d.row().subject(), d.body().replace("[[Initiator]]", "Sam Smith").replace("[[Chair]]", "Pat Pillay").replace("[[Charges (numbered list)]]", "1. Late on 1 October")),
                HR_USER, ISSUER);
        h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        assertThat(h.savedCaseEvents.size()).isEqualTo(1);
        assertThat(h.savedCaseEvents.get(0).getAction()).isEqualTo("LETTER_ISSUED");
        assertThat(h.savedCaseEvents.get(0).isVisibleToEmployee()).isTrue();
    }

    @Test
    void aLetterIsVoidedWithAReasonAndOnlyOnce() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = h.service.updateDraft(TENANT, draftFor(emp, "GENERAL_LETTER").row().id(), new DraftRequest("Hello", "Dear Thandi"), HR_USER, ISSUER);
        h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        assertThatThrownBy(() -> h.service.voidLetter(TENANT, d.row().id(), new VoidRequest(" "), HR_USER, ISSUER)).hasMessageContaining("reason");
        Detail v = h.service.voidLetter(TENANT, d.row().id(), new VoidRequest("Wrong person"), HR_USER, ISSUER);
        assertThat(v.row().status()).isEqualTo("VOID");
        assertThat(v.voidReason()).isEqualTo("Wrong person");
        assertThat(v.actions()).isEmpty();
        assertThatThrownBy(() -> h.service.voidLetter(TENANT, d.row().id(), new VoidRequest("Again"), HR_USER, ISSUER)).hasMessageContaining("already void");
    }

    @Test
    void aDraftCanBeDiscardedWithoutAFile() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "GENERAL_LETTER");
        Detail v = h.service.voidLetter(TENANT, d.row().id(), new VoidRequest("Not needed"), HR_USER, ISSUER);
        assertThat(v.row().status()).isEqualTo("VOID");
        assertThat(h.files.stored.isEmpty()).isTrue();
    }

    // ── Reading ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void theFileIsSentOnlyWhileItStillMatchesWhatWasIssued() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail draft = draftFor(emp, "GENERAL_LETTER");
        assertThatThrownBy(() -> h.service.download(TENANT, draft.row().id(), READER, HR_USER)).hasMessageContaining("Issue it first");
        Detail d = h.service.updateDraft(TENANT, draft.row().id(), new DraftRequest("Hello", "Dear Thandi"), HR_USER, ISSUER);
        h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        LetterService.Pdf pdf = h.service.download(TENANT, d.row().id(), READER, HR_USER);
        assertThat(pdf.fileName().endsWith(".pdf")).isTrue();
        assertThat(pdf.bytes().length > 0).isTrue();
        h.files.tamper = true;
        assertThatThrownBy(() -> h.service.download(TENANT, d.row().id(), READER, HR_USER)).hasMessageContaining("no longer matches");
        assertThatThrownBy(() -> h.service.download(TENANT, d.row().id(), NOTHING, HR_USER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theListCanBeNarrowedAndHidesLettersOnCasesThePersonMayNotSee() {
        UUID a = h.employee("Thandi", "Mokoena");
        UUID b = h.employee("Sipho", "Nkosi");
        draftFor(a, "GENERAL_LETTER");
        Detail second = draftFor(b, "GENERAL_LETTER");
        h.service.voidLetter(TENANT, second.row().id(), new VoidRequest("Not needed"), HR_USER, ISSUER);
        HrErCase secret = h.discipline(a, true);
        h.service.prepare(TENANT, new PrepareRequest(template("NOTICE_OF_HEARING"), null, secret.getId()), HR_USER, java.util.Set.of("HR_LETTER_ISSUE", "HR_ER_READ", "HR_SENSITIVE_READ"));

        assertThat(h.service.list(TENANT, null, null, null, ISSUER, OTHER_OFFICER).size()).isEqualTo(2);
        assertThat(h.service.list(TENANT, b, null, null, ISSUER, OTHER_OFFICER).size()).isEqualTo(1);
        assertThat(h.service.list(TENANT, null, null, "VOID", ISSUER, OTHER_OFFICER).size()).isEqualTo(1);
        assertThat(h.service.list(TENANT, null, secret.getId(), null, ISSUER, OTHER_OFFICER)).isEmpty();
        assertThat(h.service.list(TENANT, null, null, null, java.util.Set.of("HR_LETTER_READ", "HR_ER_READ", "HR_SENSITIVE_READ"), OTHER_OFFICER).size()).isEqualTo(3);
        assertThat(h.service.list(TENANT, null, null, null, ISSUER, HR_USER).size()).isEqualTo(3);
    }

    // ── The employee ──────────────────────────────────────────────────────────────────────────────

    private UUID issuedTo(UUID employee, String code, String body) {
        Detail d = h.service.prepare(TENANT, new PrepareRequest(template(code), employee, null), HR_USER, ISSUER);
        h.service.updateDraft(TENANT, d.row().id(), new DraftRequest("Subject " + code, body), HR_USER, ISSUER);
        h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        return d.row().id();
    }

    @Test
    void anEmployeeSeesOnlyTheirOwnIssuedLetters() {
        UUID thandi = h.employee("Thandi", "Mokoena");
        UUID sipho = h.employee("Sipho", "Nkosi");
        UUID me = h.signedInAs(thandi);
        UUID issued = issuedTo(thandi, "GENERAL_LETTER", "Dear Thandi");
        draftFor(thandi, "GENERAL_LETTER");
        UUID voided = issuedTo(thandi, "GENERAL_LETTER", "Dear Thandi again");
        h.service.voidLetter(TENANT, voided, new VoidRequest("Wrong"), HR_USER, ISSUER);
        UUID others = issuedTo(sipho, "GENERAL_LETTER", "Dear Sipho");

        List<Mine> mine = h.service.mine(TENANT, me);
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).id()).isEqualTo(issued);
        assertThatThrownBy(() -> h.service.mineOne(TENANT, others, me)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> h.service.mineOne(TENANT, voided, me)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(h.service.minePdf(TENANT, issued, me).bytes().length > 0).isTrue();
        assertThatThrownBy(() -> h.service.minePdf(TENANT, others, me)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anEmployeeWithoutALinkedRecordIsToldSo() {
        assertThatThrownBy(() -> h.service.mine(TENANT, UUID.randomUUID())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not linked");
    }

    @Test
    void anEmployeeConfirmsReceiptOnceAndOnlyWhenAsked() {
        UUID thandi = h.employee("Thandi", "Mokoena");
        UUID me = h.signedInAs(thandi);
        UUID asks = issuedTo(thandi, "SUSPENSION_LETTER", "You are suspended on full pay.");
        UUID doesNotAsk = issuedTo(thandi, "GENERAL_LETTER", "Dear Thandi");

        assertThat(h.service.mineOne(TENANT, asks, me).canAcknowledge()).isTrue();
        Mine done = h.service.acknowledge(TENANT, asks, me);
        assertThat(done.canAcknowledge()).isFalse();
        assertThat(done.acknowledgedAt() != null).isTrue();
        assertThatThrownBy(() -> h.service.acknowledge(TENANT, asks, me)).hasMessageContaining("already");
        assertThatThrownBy(() -> h.service.acknowledge(TENANT, doesNotAsk, me)).hasMessageContaining("does not ask");
        assertThat(h.savedEvents.stream().anyMatch(e -> e.getAction().equals("ACKNOWLEDGED") && e.getNote().contains("fingerprint"))).isTrue();
    }

    @Test
    void someoneElseCannotConfirmReceiptForAnEmployee() {
        UUID thandi = h.employee("Thandi", "Mokoena");
        UUID sipho = h.employee("Sipho", "Nkosi");
        UUID siphoUser = h.signedInAs(sipho);
        UUID asks = issuedTo(thandi, "SUSPENSION_LETTER", "You are suspended on full pay.");
        assertThatThrownBy(() -> h.service.acknowledge(TENANT, asks, siphoUser)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmingReceiptOfACaseLetterIsNotedOnTheCase() {
        UUID thandi = h.employee("Thandi", "Mokoena");
        UUID me = h.signedInAs(thandi);
        HrErCase c = h.discipline(thandi, false);
        Detail d = h.service.prepare(TENANT, new PrepareRequest(template("SUSPENSION_LETTER"), null, c.getId()), HR_USER, ISSUER);
        h.service.updateDraft(TENANT, d.row().id(), new DraftRequest("Suspension", "You are suspended on full pay."), HR_USER, ISSUER);
        h.service.issue(TENANT, d.row().id(), HR_USER, ISSUER);
        h.service.acknowledge(TENANT, d.row().id(), me);
        assertThat(h.savedCaseEvents.get(h.savedCaseEvents.size() - 1).getAction()).isEqualTo("LETTER_ACKNOWLEDGED");
    }

    @Test
    void aLetterNeverCarriesPayOrIdentityNumbers() {
        UUID emp = h.employee("Thandi", "Mokoena");
        Detail d = draftFor(emp, "CERTIFICATE_OF_SERVICE");
        HrLetter l = h.savedLetters.get(0);
        assertThat(l.getBody().contains("Remuneration at the date employment ended: [[Remuneration (type in)]]")).isTrue();
        assertThat(d.body().toLowerCase().contains("id number")).isFalse();
        HrLetterTemplate t = h.savedTemplates.get(0);
        assertThat(t.getId() != null).isTrue();
    }
}
