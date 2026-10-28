package za.co.handyflow.platform.recruiter.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.*;
import za.co.handyflow.platform.recruiter.dto.RecruiterApprovalDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RecruiterApprovalAndRetentionTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID maker = UUID.randomUUID(), checker = UUID.randomUUID();

    private final RecJobRepository jobs = mock(RecJobRepository.class);
    private final RecApplicationRepository applications = mock(RecApplicationRepository.class);
    private final RecApplicantRepository applicants = mock(RecApplicantRepository.class);
    private final RecSettingsRepository settingsRepo = mock(RecSettingsRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CvStorage cv = mock(CvStorage.class);
    private final RecruiterApprovalNotifier notifier = mock(RecruiterApprovalNotifier.class);
    private final RecruiterApprovers approvers = mock(RecruiterApprovers.class);
    private final RecruiterApprovalService approvals = new RecruiterApprovalService(jobs, applications, settingsRepo, jdbc, notifier, approvers);
    private final RecruiterRetentionService retention = new RecruiterRetentionService(applicants, applications, approvals, cv, jdbc);

    @BeforeEach
    void everyoneMayApprove() {
        when(approvers.anyoneBut(any(), any())).thenReturn(true);
        when(approvers.mayApprove(any(), any())).thenReturn(true);
    }

    private final List<String> sqls = new java.util.ArrayList<>();
    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    private static void set(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static Instant ago(int months) { return NOW.atZone(ZoneId.of("Africa/Johannesburg")).minusMonths(months).minusDays(1).toInstant(); }

    private void settings(boolean jobs, boolean offers, int months) {
        RecSettings s = RecSettings.defaults(tenant.getValue());
        s.update(jobs, offers, months, maker);
        when(settingsRepo.findById(tenant.getValue())).thenReturn(Optional.of(s));
    }

    private RecJob draftJob() {
        RecJob j = RecJob.create(tenant, "Clerk", "Admin", "Pretoria", "FULL_TIME", "MID", "Filing and capturing", null, null,
                new BigDecimal("15000"), new BigDecimal("20000"), false, null, maker);
        when(jobs.findByIdAndTenantId(j.getId(), tenant)).thenReturn(Optional.of(j));
        return j;
    }

    private RecApplication offerApplication(UUID applicant) {
        RecApplication a = RecApplication.create(tenant, UUID.randomUUID(), applicant, "CAREERS_PAGE", null);
        a.moveToStage("OFFER");
        a.recordOfferTerms(new BigDecimal("30000"), "MONTHLY", java.time.LocalDate.of(2026, 12, 1), "Medical aid");
        when(applications.findByIdAndTenantId(a.getId(), tenant)).thenReturn(Optional.of(a));
        return a;
    }

    // ── Settings ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void approvalIsOffUntilTheCompanySwitchesItOn() {
        SettingsView v = approvals.settingsView(tenant);
        assertThat(v.requireJobApproval()).isFalse();
        assertThat(v.requireOfferApproval()).isFalse();
        assertThat(v.retentionMonths()).isEqualTo(12);
        approvals.requirePublishable(tenant, draftJob());
    }

    @Test
    void settingsAreSavedAndTheRetentionPeriodIsChecked() {
        SettingsView v = approvals.updateSettings(tenant, new SettingsRequest(true, null, 24), maker);
        assertThat(v.requireJobApproval()).isTrue();
        assertThat(v.requireOfferApproval()).isFalse();
        assertThat(v.retentionMonths()).isEqualTo(24);
        verify(settingsRepo).save(any());
        assertThatThrownBy(() -> approvals.updateSettings(tenant, new SettingsRequest(null, null, 2), maker))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 3 and 60");
    }

    @Test
    void thePrivacyNoticeLinkIsCheckedKeptAndCanBeRemoved() {
        SettingsView v = approvals.updateSettings(tenant, new SettingsRequest(null, null, null, " https://acme.co.za/privacy "), maker);
        assertThat(v.privacyNoticeUrl()).isEqualTo("https://acme.co.za/privacy");
        assertThatThrownBy(() -> approvals.updateSettings(tenant, new SettingsRequest(null, null, null, "javascript:alert(1)"), maker))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https://");
        assertThat(approvals.updateSettings(tenant, new SettingsRequest(true, null, null, ""), maker).privacyNoticeUrl()).isNull();
    }

    @Test
    void aRequestNobodyElseCanAnswerIsRefusedWhenItIsSent() {
        settings(true, false, 12);
        RecJob job = draftJob();
        when(approvers.anyoneBut(tenant, maker)).thenReturn(false);
        assertThatThrownBy(() -> approvals.submitJob(tenant, job.getId(), maker)).hasMessageContaining("Nobody else can approve");
        assertThat(job.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
    }

    // ── Jobs ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    void aJobNeedsApprovalFromSomeoneElseBeforeItIsPublished() {
        settings(true, false, 12);
        RecJob job = draftJob();
        assertThatThrownBy(() -> approvals.requirePublishable(tenant, job)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("approved before");

        approvals.submitJob(tenant, job.getId(), maker);
        assertThat(job.getApprovalStatus()).isEqualTo("SUBMITTED");
        assertThatThrownBy(() -> approvals.requirePublishable(tenant, job)).hasMessageContaining("waiting");
        assertThatThrownBy(() -> approvals.decideJob(tenant, job.getId(), new DecisionRequest(true, null), maker)).hasMessageContaining("yourself");

        ApprovalView v = approvals.decideJob(tenant, job.getId(), new DecisionRequest(true, "Budget agreed"), checker);
        assertThat(v.status()).isEqualTo("APPROVED");
        assertThat(v.note()).isEqualTo("Budget agreed");
        approvals.requirePublishable(tenant, job);
    }

    @Test
    void aJobThatIsNotApprovedSaysWhyAndCanBeSentAgain() {
        settings(true, false, 12);
        RecJob job = draftJob();
        approvals.submitJob(tenant, job.getId(), maker);
        assertThatThrownBy(() -> approvals.decideJob(tenant, job.getId(), new DecisionRequest(false, ""), checker)).hasMessageContaining("why");
        approvals.decideJob(tenant, job.getId(), new DecisionRequest(false, "Salary band too high"), checker);
        assertThat(job.getApprovalStatus()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> approvals.requirePublishable(tenant, job)).hasMessageContaining("not approved");
        approvals.submitJob(tenant, job.getId(), maker);
        assertThat(job.getApprovalStatus()).isEqualTo("SUBMITTED");
        assertThat(job.getApprovalNote()).isNull();
    }

    @Test
    void theViewSaysWhetherTheCallerMayDecide() {
        settings(true, false, 12);
        RecJob job = draftJob();
        approvals.submitJob(tenant, job.getId(), maker);
        assertThat(approvals.jobApproval(tenant, job.getId(), maker).canDecide()).isFalse();
        assertThat(approvals.jobApproval(tenant, job.getId(), checker).canDecide()).isTrue();
    }

    // ── Offers ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anOfferLetterIsHeldBackUntilTheOfferIsApprovedWhenTheCompanyAsks() {
        settings(false, true, 12);
        RecApplication app = offerApplication(UUID.randomUUID());
        assertThatThrownBy(() -> approvals.requireOfferSendable(tenant, app)).hasMessageContaining("approved before");
        approvals.submitOffer(tenant, app.getId(), maker);
        assertThatThrownBy(() -> approvals.decideOffer(tenant, app.getId(), new DecisionRequest(true, null), maker)).hasMessageContaining("yourself");
        approvals.decideOffer(tenant, app.getId(), new DecisionRequest(true, null), checker);
        approvals.requireOfferSendable(tenant, app);
    }

    @Test
    void changingTheTermsAfterApprovalTakesTheApprovalAway() {
        settings(false, true, 12);
        RecApplication app = offerApplication(UUID.randomUUID());
        approvals.submitOffer(tenant, app.getId(), maker);
        approvals.decideOffer(tenant, app.getId(), new DecisionRequest(true, null), checker);

        app.recordOfferTerms(new BigDecimal("30000.00"), "MONTHLY", java.time.LocalDate.of(2026, 12, 1), "Medical aid");   // the same terms
        assertThat(app.getOfferApprovalStatus()).isEqualTo("APPROVED");
        app.recordOfferTerms(new BigDecimal("35000"), "MONTHLY", java.time.LocalDate.of(2026, 12, 1), "Medical aid");
        assertThat(app.getOfferApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        assertThatThrownBy(() -> approvals.requireOfferSendable(tenant, app)).hasMessageContaining("approved before");
    }

    @Test
    void onlyAnApplicationAtTheOfferStageWithASalaryCanBeSentForApproval() {
        settings(false, true, 12);
        RecApplication app = RecApplication.create(tenant, UUID.randomUUID(), UUID.randomUUID(), "CAREERS_PAGE", null);
        when(applications.findByIdAndTenantId(app.getId(), tenant)).thenReturn(Optional.of(app));
        assertThatThrownBy(() -> approvals.submitOffer(tenant, app.getId(), maker)).hasMessageContaining("offer stage");
        app.moveToStage("OFFER");
        assertThatThrownBy(() -> approvals.submitOffer(tenant, app.getId(), maker)).hasMessageContaining("salary");
    }

    // ── Retention ─────────────────────────────────────────────────────────────────────────────────

    private RecApplicant applicant(Instant updated, String cvUrl) {
        RecApplicant a = RecApplicant.create(tenant, "Thandi", "Mokoena", "Thandi@Example.com", "0821234567", "Soweto", "https://li/x", null, cvUrl, "cv.pdf");
        set(a, "updatedAt", updated);
        when(applicants.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private RecApplication application(RecApplicant a, String stage, Instant at) {
        RecApplication x = RecApplication.create(tenant, UUID.randomUUID(), a.getId(), "CAREERS_PAGE", "A friend");
        set(x, "stage", stage);
        set(x, "updatedAt", at);
        set(x, "stageChangedAt", at);
        return x;
    }

    @Test
    void onlyUnsuccessfulApplicantsPastThePeriodAreListed() {
        settings(false, false, 12);
        RecApplicant gone = applicant(ago(14), "local://a.pdf");
        RecApplicant live = applicant(ago(14), null);
        RecApplicant recent = applicant(ago(2), null);
        RecApplication rejected = application(gone, "REJECTED", ago(13));
        RecApplication interviewing = application(live, "INTERVIEW", ago(13));
        when(applicants.notChangedSince(any(), any())).thenReturn(List.of(gone, live));
        when(applications.findByApplicantIdIn(any())).thenReturn(List.of(rejected, interviewing));

        List<RemovalCandidate> due = retention.due(tenant, NOW);

        assertThat(due.size()).isEqualTo(1);
        assertThat(due.get(0).applicantId()).isEqualTo(gone.getId());
        assertThat(due.get(0).hasCv()).isTrue();
        assertThat(due.get(0).applications()).isEqualTo(1);
        assertThat(recent.isAnonymised()).isFalse();
    }

    @Test
    void removingTakesTheNameContactDetailsAndCvAndTheFreeText() {
        settings(false, false, 12);
        RecApplicant a = applicant(ago(14), "local://a.pdf");
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(i -> { sqls.add(i.getArgument(0)); return 1; });
        String oldToken = a.getPortalToken();
        when(applications.findByApplicantId(a.getId())).thenReturn(List.of(application(a, "REJECTED", ago(13))));

        RemovalResult r = retention.remove(tenant, List.of(a.getId()), maker, NOW);

        assertThat(r.removed()).isEqualTo(1);
        assertThat(r.refused().size()).isEqualTo(0);
        assertThat(a.isAnonymised()).isTrue();
        assertThat(a.getFirstName()).isEqualTo("Removed");
        assertThat(a.getEmail()).isEqualTo("removed-" + a.getId() + "@removed.invalid");
        assertThat(a.getPhone()).isNull();
        assertThat(a.getLocation()).isNull();
        assertThat(a.getLinkedinUrl()).isNull();
        assertThat(a.getCvUrl()).isNull();
        assertThat(a.getPortalToken().equals(oldToken)).isFalse();
        verify(cv).delete("local://a.pdf");
        verify(applicants).save(a);
        assertThat(sqls.stream().anyMatch(q -> q.startsWith("UPDATE rec_applications SET notes = NULL"))).isTrue();
        assertThat(sqls.stream().anyMatch(q -> q.startsWith("UPDATE rec_interviews SET notes = NULL"))).isTrue();
        assertThat(sqls.stream().anyMatch(q -> q.startsWith("UPDATE rec_stage_history SET notes = NULL"))).isTrue();
        assertThat(sqls.stream().anyMatch(q -> q.startsWith("INSERT INTO rec_retention_log"))).isTrue();
    }

    @Test
    void someoneStillInTheRunningOrNotYetPastThePeriodIsRefusedWithAReasonAndKeepsTheirCv() {
        settings(false, false, 12);
        RecApplicant live = applicant(ago(14), "local://b.pdf");
        when(applications.findByApplicantId(live.getId())).thenReturn(List.of(application(live, "OFFER", ago(13))));
        RecApplicant recent = applicant(ago(2), "local://c.pdf");
        when(applications.findByApplicantId(recent.getId())).thenReturn(List.of(application(recent, "REJECTED", ago(2))));
        UUID stranger = UUID.randomUUID();

        RemovalResult r = retention.remove(tenant, List.of(live.getId(), recent.getId(), stranger), maker, NOW);

        assertThat(r.removed()).isEqualTo(0);
        assertThat(r.refused().size()).isEqualTo(3);
        assertThat(r.refused().get(0).reason()).contains("still open");
        assertThat(r.refused().get(1).reason()).contains("has not ended");
        assertThat(r.refused().get(2).reason()).contains("No such applicant");
        assertThat(live.isAnonymised()).isFalse();
        assertThat(live.getCvUrl()).isEqualTo("local://b.pdf");
        verify(cv, never()).delete(anyString());
        verify(applicants, never()).save(any());
    }

    @Test
    void ifTheCvFileCannotBeDeletedNothingIsRemoved() {
        settings(false, false, 12);
        RecApplicant a = applicant(ago(14), "local://a.pdf");
        when(applications.findByApplicantId(a.getId())).thenReturn(List.of(application(a, "WITHDRAWN", ago(13))));
        CvStorage broken = new CvStorage() {
            public String store(byte[] bytes, String fileName) { return null; }
            public byte[] retrieve(String reference) { return null; }
            public void delete(String reference) { throw new IllegalStateException("disk"); }
        };

        RemovalResult r = new RecruiterRetentionService(applicants, applications, approvals, broken, jdbc).remove(tenant, List.of(a.getId()), maker, NOW);

        assertThat(r.removed()).isEqualTo(0);
        assertThat(r.refused().get(0).reason()).contains("CV file could not be deleted");
        assertThat(a.isAnonymised()).isFalse();
        assertThat(a.getFirstName()).isEqualTo("Thandi");
        verify(applicants, never()).save(any());
    }

    @Test
    void anApplicantOfAnotherCompanyIsNotTouched() {
        settings(false, false, 12);
        RecApplicant other = RecApplicant.create(TenantId.of(UUID.randomUUID()), "Sam", "Other", "sam@example.com", null, null, null, null, null, null);
        set(other, "updatedAt", ago(20));
        when(applicants.findById(other.getId())).thenReturn(Optional.of(other));
        RemovalResult r = retention.remove(tenant, List.of(other.getId()), maker, NOW);
        assertThat(r.removed()).isEqualTo(0);
        assertThat(r.refused().get(0).reason()).contains("No such applicant");
        assertThat(other.isAnonymised()).isFalse();
    }

    @Test
    void choosingNobodyIsAnError() {
        assertThatThrownBy(() -> retention.remove(tenant, List.of(), maker, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Notifications and the offer PDF ───────────────────────────────────────────────────────────

    @Test
    void sendingAJobForApprovalAsksTheApprovers_andTheOutcomeGoesBackToTheSubmitter() {
        settings(true, false, 12);
        RecJob job = draftJob();
        approvals.submitJob(tenant, job.getId(), maker);
        verify(notifier).requested(tenant, true, job.getId(), "Clerk", maker);
        approvals.decideJob(tenant, job.getId(), new DecisionRequest(true, "Fine"), checker);
        verify(notifier).decided(tenant, true, job.getId(), "Clerk", maker, checker, true);
    }

    @Test
    void aRefusedApprovalIsNotAnnounced() {
        settings(true, false, 12);
        RecJob job = draftJob();
        approvals.submitJob(tenant, job.getId(), maker);
        assertThatThrownBy(() -> approvals.decideJob(tenant, job.getId(), new DecisionRequest(true, null), maker)).hasMessageContaining("yourself");
        verify(notifier, never()).decided(any(), anyBoolean(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void anOfferThatNeedsApprovalCannotBeDownloadedUntilApproved() {
        settings(false, true, 12);
        RecApplication a = offerApplication(UUID.randomUUID());
        assertThatThrownBy(() -> approvals.requireOfferPdf(tenant, a.getId())).hasMessageContaining("Send it for approval");
        approvals.submitOffer(tenant, a.getId(), maker);
        assertThatThrownBy(() -> approvals.requireOfferPdf(tenant, a.getId())).hasMessageContaining("waiting for approval");
        approvals.decideOffer(tenant, a.getId(), new DecisionRequest(true, null), checker);
        approvals.requireOfferPdf(tenant, a.getId());
    }

    @Test
    void theOfferPdfIsNotBlockedWhenTheCompanyDoesNotUseApproval() {
        settings(false, false, 12);
        RecApplication a = offerApplication(UUID.randomUUID());
        approvals.requireOfferPdf(tenant, a.getId());
    }
}
