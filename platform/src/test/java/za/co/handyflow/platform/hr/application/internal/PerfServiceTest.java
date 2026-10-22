package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static za.co.handyflow.platform.hr.application.internal.PerfHarness.*;

class PerfServiceTest {

    private final PerfHarness h = new PerfHarness();
    private final UUID boss = h.employee("Bongani", "Boss");
    private final UUID hr = h.employee("Hanna", "Hart", boss);
    private final UUID ann = h.employee("Ann", "Adams", boss);
    private final UUID ben = h.employee("Ben", "Brown", boss);
    private final UUID solo = h.employee("Sam", "Solo");
    private final UUID hrUser = h.signedInAs(hr);
    private final UUID bossUser = h.signedInAs(boss);
    private final UUID annUser = h.signedInAs(ann);
    private final UUID benUser = h.signedInAs(ben);
    private final UUID soloUser = h.signedInAs(solo);
    private final LocalDate today = today();

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────

    private CycleDetail openCycle(boolean feedback360) {
        CycleDetail c = h.cycles.create(TENANT, new CycleRequest("Mid-year 2026", today.minusDays(120), today.minusDays(10), today.plusDays(7), today.plusDays(14), feedback360), hrUser, HR);
        return h.cycles.launch(TENANT, c.row().id(), hrUser, HR);
    }

    private UUID reviewOf(CycleDetail c, UUID employee) {
        return c.reviews().stream().filter(r -> r.employeeId().equals(employee)).findFirst().orElseThrow().id();
    }

    private static final String REASONS = "Delivered the project on time and mentored two new starters.";

    /** Ann's review taken as far as the manager's rating. */
    private UUID annSubmitted(CycleDetail c) {
        UUID id = reviewOf(c, ann);
        h.reviews.submitSelf(TENANT, id, new SelfRequest(4, "A good half-year."), annUser, NONE);
        h.reviews.submitManager(TENANT, id, new ManagerRequest(4, REASONS), bossUser, NONE);
        return id;
    }

    private ReviewDetail view(UUID review, UUID user, Set<String> held) { return h.reviews.get(TENANT, review, held, user); }

    private GoalView goal(UUID user, UUID employee, String title) {
        return h.goals.create(TENANT, new GoalRequest(employee, null, title, "Detail", "Measured by X", today.plusDays(60)), user, NONE);
    }

    private PlanDetail draftPlan(UUID employee, LocalDate start, LocalDate end, UUID caseId) {
        return h.plans.create(TENANT, new PlanRequest(employee, null, caseId, "Reduce invoice errors", "Error rate above five percent.", "Below one percent for four weeks.",
                "Weekly coaching and a checklist.", start, end), hrUser, HR);
    }

    private PlanDetail runningPlan(UUID employee) {
        PlanDetail d = draftPlan(employee, today.minusDays(40), today.minusDays(1), null);
        return h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today.minusDays(40)), hrUser, HR);
    }

    // ── Cycles ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void openingACycleMakesAReviewForEveryoneWhoWorkedInThePeriod() {
        h.startedOn("Late", "Starter", boss, today.minusDays(5));
        h.leftOn("Early", "Leaver", boss, today.minusDays(200));
        CycleDetail c = openCycle(false);
        assertThat(c.row().status()).isEqualTo("OPEN");
        assertThat(c.reviews().size()).isEqualTo(5);
        assertThat(c.reviews().stream().filter(r -> r.employeeId().equals(ann)).findFirst().orElseThrow().reviewerId()).isEqualTo(boss);
        assertThat(c.reviews().stream().filter(r -> r.employeeId().equals(solo)).findFirst().orElseThrow().reviewerId()).isNull();
        assertThat(c.withoutReviewer()).isEqualTo(2);
        assertThat(c.row().awaitingSelf()).isEqualTo(5);
        assertThat(c.events().get(c.events().size() - 1).action()).isEqualTo("OPENED");
    }

    @Test
    void aManagerWhoHasLeftIsNotUsedAsReviewer() {
        h.terminate(boss);
        CycleDetail c = openCycle(false);
        assertThat(c.reviews().stream().filter(r -> r.employeeId().equals(ann)).findFirst().orElseThrow().reviewerId()).isNull();
        assertThat(c.reviews().stream().noneMatch(r -> r.employeeId().equals(boss))).isTrue();
    }

    @Test
    void onlyHrRunsCyclesButReadersCanLook() {
        CycleRequest req = new CycleRequest("Cycle", today.minusDays(100), today.minusDays(10), today.plusDays(7), today.plusDays(14), false);
        assertThatThrownBy(() -> h.cycles.create(TENANT, req, hrUser, READER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.cycles.create(TENANT, req, hrUser, NONE)).isInstanceOf(AccessDeniedException.class);
        CycleDetail c = h.cycles.create(TENANT, req, hrUser, HR);
        assertThat(h.cycles.cycles(TENANT, READER).size()).isEqualTo(1);
        assertThat(h.cycles.cycle(TENANT, c.row().id(), READER, hrUser).actions()).isEmpty();
        assertThatThrownBy(() -> h.cycles.cycles(TENANT, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.cycles.launch(TENANT, c.row().id(), hrUser, READER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aCycleIsOpenedOnceAndNeedsSomeoneToReview() {
        CycleDetail c = openCycle(false);
        assertThatThrownBy(() -> h.cycles.launch(TENANT, c.row().id(), hrUser, HR)).isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
        PerfHarness empty = new PerfHarness();
        CycleDetail d = empty.cycles.create(TENANT, new CycleRequest("Cycle", today.minusDays(100), today.minusDays(10), today.plusDays(7), today.plusDays(14), false), UUID.randomUUID(), HR);
        assertThatThrownBy(() -> empty.cycles.launch(TENANT, d.row().id(), UUID.randomUUID(), HR)).isInstanceOf(IllegalStateException.class).hasMessageContaining("nobody");
    }

    @Test
    void aDraftCycleCanBeChangedOrDeletedButAnOpenOneCannot() {
        CycleDetail d = h.cycles.create(TENANT, new CycleRequest("Draft", today.minusDays(100), today.minusDays(10), today.plusDays(7), today.plusDays(14), false), hrUser, HR);
        CycleDetail edited = h.cycles.update(TENANT, d.row().id(), new CycleRequest("Renamed", today.minusDays(100), today.minusDays(10), today.plusDays(7), today.plusDays(14), true), hrUser, HR);
        assertThat(edited.row().name()).isEqualTo("Renamed");
        assertThat(edited.row().include360()).isTrue();
        assertThat(edited.actions()).contains("LAUNCH", "DELETE");
        h.cycles.delete(TENANT, d.row().id(), HR);
        verify(h.cycleRepo).delete(h.cycleStore.get(0));
        CycleDetail open = openCycle(false);
        assertThatThrownBy(() -> h.cycles.update(TENANT, open.row().id(), new CycleRequest("X", today.minusDays(100), today.minusDays(10), today.plusDays(7), today.plusDays(14), false), hrUser, HR))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> h.cycles.delete(TENANT, open.row().id(), HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCycleCannotCloseUntilEveryReviewIsReleasedOrLeftOut() {
        UUID hr2 = h.employee("Hugo", "Hill");
        UUID hr2User = h.signedInAs(hr2);
        CycleDetail c = openCycle(false);
        assertThatThrownBy(() -> h.cycles.close(TENANT, c.row().id(), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not released");
        for (ReviewRow r : c.reviews()) h.reviews.exclude(TENANT, r.id(), new ReasonRequest("Not part of this round."), r.employeeId().equals(hr) ? hr2User : hrUser, HR);
        CycleDetail closed = h.cycles.close(TENANT, c.row().id(), hrUser, HR);
        assertThat(closed.row().status()).isEqualTo("CLOSED");
        assertThat(closed.row().excluded()).isEqualTo(6);
        assertThatThrownBy(() -> h.cycles.close(TENANT, c.row().id(), hrUser, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aNewJoinerCanBeAddedOnceAndOnlyIfTheyWorkedInThePeriod() {
        CycleDetail c = openCycle(false);
        UUID late = h.employee("Lerato", "Late", boss);
        CycleDetail added = h.cycles.addReview(TENANT, c.row().id(), new AddReviewRequest(late), hrUser, HR);
        assertThat(added.reviews().size()).isEqualTo(6);
        assertThatThrownBy(() -> h.cycles.addReview(TENANT, c.row().id(), new AddReviewRequest(late), hrUser, HR)).isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
        UUID tooNew = h.startedOn("Too", "New", boss, today.minusDays(3));
        assertThatThrownBy(() -> h.cycles.addReview(TENANT, c.row().id(), new AddReviewRequest(tooNew), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("did not work");
    }

    // ── Reviews ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void theEmployeeWritesTheSelfReviewOnce() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        assertThat(view(id, annUser, NONE).actions()).containsExactly("SELF");
        ReviewDetail d = h.reviews.submitSelf(TENANT, id, new SelfRequest(4, "A good half-year."), annUser, NONE);
        assertThat(d.row().status()).isEqualTo("AWAITING_MANAGER");
        assertThat(d.selfRating()).isEqualTo(4);
        assertThatThrownBy(() -> h.reviews.submitSelf(TENANT, id, new SelfRequest(5, "x"), annUser, NONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> h.reviews.submitSelf(TENANT, id, new SelfRequest(5, "x"), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.reviews.submitSelf(TENANT, id, new SelfRequest(5, "x"), benUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> h.reviews.submitSelf(TENANT, reviewOf(c, ben), new SelfRequest(9, "x"), benUser, NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theManagerWaitsForTheSelfReviewOrItsDueDate() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        assertThatThrownBy(() -> h.reviews.submitManager(TENANT, id, new ManagerRequest(3, REASONS), bossUser, NONE)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Wait");
        h.reviews.submitSelf(TENANT, id, new SelfRequest(3, null), annUser, NONE);
        ReviewDetail d = h.reviews.submitManager(TENANT, id, new ManagerRequest(3, REASONS), bossUser, NONE);
        assertThat(d.row().status()).isEqualTo("SUBMITTED");
        assertThat(d.finalRating()).isEqualTo(3);
    }

    @Test
    void afterTheSelfReviewIsDueTheManagerMayRateWithoutIt() {
        CycleDetail created = h.cycles.create(TENANT, new CycleRequest("Late cycle", today.minusDays(120), today.minusDays(10), today.minusDays(1), today.plusDays(10), false), hrUser, HR);
        CycleDetail c = h.cycles.launch(TENANT, created.row().id(), hrUser, HR);
        ReviewDetail d = h.reviews.submitManager(TENANT, reviewOf(c, ann), new ManagerRequest(3, REASONS), bossUser, NONE);
        assertThat(d.row().status()).isEqualTo("SUBMITTED");
        assertThat(d.selfRating()).isNull();
    }

    @Test
    void onlyTheReviewerRatesAndAnExtremeRatingNeedsReasons() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        h.reviews.submitSelf(TENANT, id, new SelfRequest(3, null), annUser, NONE);
        assertThatThrownBy(() -> h.reviews.submitManager(TENANT, id, new ManagerRequest(3, REASONS), hrUser, HR)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.reviews.submitManager(TENANT, id, new ManagerRequest(2, "Short note."), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20 characters");
        assertThatThrownBy(() -> h.reviews.submitManager(TENANT, id, new ManagerRequest(3, ""), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(h.reviewStore.stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow().getStatus()).isEqualTo("AWAITING_MANAGER");
    }

    @Test
    void theEmployeeSeesTheManagersSideOnlyOnceTheResultIsReleased() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        ReviewDetail before = view(id, annUser, NONE);
        assertThat(before.managerRating()).isNull();
        assertThat(before.managerComment()).isNull();
        assertThat(before.finalRating()).isNull();
        assertThat(before.row().rating()).isNull();
        assertThat(before.events()).isEmpty();
        assertThat(before.actions()).isEmpty();
        ReviewDetail boss1 = view(id, bossUser, NONE);
        assertThat(boss1.managerRating()).isEqualTo(4);
        assertThat(boss1.selfComment()).isEqualTo("A good half-year.");
        assertThat(boss1.events().isEmpty()).isFalse();
        h.reviews.share(TENANT, id, hrUser, HR);
        ReviewDetail after = view(id, annUser, NONE);
        assertThat(after.managerRating()).isEqualTo(4);
        assertThat(after.managerComment()).isEqualTo(REASONS);
        assertThat(after.finalRating()).isEqualTo(4);
        assertThat(after.row().rating()).isEqualTo(4);
        assertThat(after.actions()).containsExactly("ACKNOWLEDGE");
        assertThat(after.events()).isEmpty();
    }

    @Test
    void hrAdjustsTheFinalRatingWithAReasonAndOnlyBeforeRelease() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        assertThatThrownBy(() -> h.reviews.calibrate(TENANT, id, new CalibrateRequest(5, "short"), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.reviews.calibrate(TENANT, id, new CalibrateRequest(4, "Aligned with the department."), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.reviews.calibrate(TENANT, id, new CalibrateRequest(5, "Aligned with the department."), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        ReviewDetail d = h.reviews.calibrate(TENANT, id, new CalibrateRequest(5, "Aligned with the department distribution."), hrUser, HR);
        assertThat(d.managerRating()).isEqualTo(4);
        assertThat(d.finalRating()).isEqualTo(5);
        assertThat(d.calibrated()).isTrue();
        assertThat(d.calibrationReason()).contains("distribution");
        h.reviews.share(TENANT, id, hrUser, HR);
        ReviewDetail seen = view(id, annUser, NONE);
        assertThat(seen.finalRating()).isEqualTo(5);
        assertThat(seen.calibrated()).isFalse();
        assertThat(seen.calibrationReason()).isNull();
        assertThatThrownBy(() -> h.reviews.calibrate(TENANT, id, new CalibrateRequest(3, "Changed my mind about it."), hrUser, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aResultCannotBeReleasedBeforeTheManagerHasRated() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        assertThatThrownBy(() -> h.reviews.share(TENANT, id, hrUser, HR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> h.reviews.share(TENANT, id, hrUser, READER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theEmployeeAcknowledgesOnceAndCanReply() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        h.reviews.share(TENANT, id, hrUser, HR);
        assertThatThrownBy(() -> h.reviews.acknowledge(TENANT, id, new AcknowledgeRequest("ok"), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        ReviewDetail d = h.reviews.acknowledge(TENANT, id, new AcknowledgeRequest("I disagree with the second point."), annUser, NONE);
        assertThat(d.row().status()).isEqualTo("ACKNOWLEDGED");
        assertThat(d.employeeComment()).isEqualTo("I disagree with the second point.");
        assertThat(view(id, bossUser, NONE).employeeComment()).isEqualTo("I disagree with the second point.");
        assertThatThrownBy(() -> h.reviews.acknowledge(TENANT, id, new AcknowledgeRequest("again"), annUser, NONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void strangersCannotSeeAReviewAndReadersCannotChangeOne() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        assertThatThrownBy(() -> view(id, benUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> view(id, soloUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        ReviewDetail asReader = view(id, soloUser, READER);
        assertThat(asReader.managerRating()).isEqualTo(4);
        assertThat(asReader.actions()).isEmpty();
        assertThatThrownBy(() -> h.reviews.share(TENANT, id, soloUser, READER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void hrCannotRateOrReleaseTheirOwnReview() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, hr);
        h.reviews.submitSelf(TENANT, id, new SelfRequest(4, null), hrUser, HR);
        h.reviews.submitManager(TENANT, id, new ManagerRequest(4, REASONS), bossUser, NONE);
        assertThatThrownBy(() -> h.reviews.share(TENANT, id, hrUser, HR)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.reviews.calibrate(TENANT, id, new CalibrateRequest(5, "Because I deserve it."), hrUser, HR)).isInstanceOf(AccessDeniedException.class);
        ReviewDetail mine = view(id, hrUser, HR);
        assertThat(mine.managerRating()).isNull();
        assertThat(mine.actions()).isEmpty();
    }

    @Test
    void hrChangesTheReviewerUntilTheyHaveRated() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        assertThatThrownBy(() -> h.reviews.setReviewer(TENANT, id, new ReviewerRequest(ann, "Wrong"), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("themselves");
        assertThatThrownBy(() -> h.reviews.setReviewer(TENANT, id, new ReviewerRequest(solo, "Boss moved teams"), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        ReviewDetail d = h.reviews.setReviewer(TENANT, id, new ReviewerRequest(solo, "Boss moved teams"), hrUser, HR);
        assertThat(d.row().reviewerName()).isEqualTo("Sam Solo");
        assertThatThrownBy(() -> view(id, bossUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        h.reviews.submitSelf(TENANT, id, new SelfRequest(3, null), annUser, NONE);
        h.reviews.submitManager(TENANT, id, new ManagerRequest(3, REASONS), soloUser, NONE);
        assertThatThrownBy(() -> h.reviews.setReviewer(TENANT, id, new ReviewerRequest(boss, "Back again"), hrUser, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReviewLeftOutNeedsAReasonAndCannotBeRated() {
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ben);
        assertThatThrownBy(() -> h.reviews.exclude(TENANT, id, new ReasonRequest(" "), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.reviews.exclude(TENANT, id, new ReasonRequest("On long leave."), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        ReviewDetail d = h.reviews.exclude(TENANT, id, new ReasonRequest("On long leave."), hrUser, HR);
        assertThat(d.row().status()).isEqualTo("EXCLUDED");
        assertThat(d.excludedReason()).isEqualTo("On long leave.");
        assertThatThrownBy(() -> h.reviews.submitSelf(TENANT, id, new SelfRequest(3, null), benUser, NONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listsShowEachPersonTheirOwnTeamOrEveryone() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        assertThat(h.reviews.reviews(TENANT, null, "mine", NONE, annUser).size()).isEqualTo(1);
        assertThat(h.reviews.reviews(TENANT, null, "mine", NONE, annUser).get(0).rating()).isNull();
        assertThat(h.reviews.reviews(TENANT, null, "team", NONE, bossUser).size()).isEqualTo(3);
        assertThat(h.reviews.reviews(TENANT, null, "team", NONE, annUser).size()).isEqualTo(0);
        assertThatThrownBy(() -> h.reviews.reviews(TENANT, c.row().id(), "all", NONE, annUser)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.reviews.reviews(TENANT, null, "all", READER, soloUser)).isInstanceOf(IllegalArgumentException.class);
        List<ReviewRow> all = h.reviews.reviews(TENANT, c.row().id(), "all", READER, soloUser);
        assertThat(all.size()).isEqualTo(5);
        assertThat(all.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().rating()).isEqualTo(4);
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> h.reviews.reviews(TENANT, null, "mine", NONE, stranger)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void meSaysWhoIsWhere() {
        Me m = h.reviews.me(TENANT, bossUser, NONE);
        assertThat(m.name()).isEqualTo("Bongani Boss");
        assertThat(m.directReports()).isEqualTo(3);
        assertThat(m.canRead()).isFalse();
        Me stranger = h.reviews.me(TENANT, UUID.randomUUID(), HR);
        assertThat(stranger.employeeId()).isNull();
        assertThat(stranger.canManage()).isTrue();
    }

    // ── 360 feedback ──────────────────────────────────────────────────────────────────────────────

    private List<FeedbackView> ask(UUID review, UUID user, Set<String> held, UUID... providers) {
        return h.feedback.request(TENANT, review, new FeedbackRequestBody(List.of(providers), "PEER"), user, held);
    }

    @Test
    void theReviewerAsksColleaguesButNotTheSubjectOrThemselves() {
        CycleDetail c = openCycle(true);
        UUID id = reviewOf(c, ann);
        List<FeedbackView> asked = ask(id, bossUser, NONE, ben, solo);
        assertThat(asked.size()).isEqualTo(2);
        assertThat(asked.get(0).providerName()).isNotNull();
        assertThat(asked.get(0).strengths()).isNull();
        assertThatThrownBy(() -> ask(id, bossUser, NONE, ann)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("themselves");
        assertThatThrownBy(() -> ask(id, bossUser, NONE, boss)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reviewer");
        assertThatThrownBy(() -> ask(id, bossUser, NONE, ben)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already");
        assertThatThrownBy(() -> ask(id, annUser, NONE, hr)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.feedback.request(TENANT, id, new FeedbackRequestBody(List.of(), "PEER"), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        CycleDetail plain = openCycle(false);
        assertThatThrownBy(() -> ask(reviewOf(plain, ben), bossUser, NONE, ann)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aColleagueAnswersOnlyTheirOwnRequestsOnce() {
        CycleDetail c = openCycle(true);
        UUID id = reviewOf(c, ann);
        ask(id, bossUser, NONE, ben, solo);
        List<FeedbackTask> tasks = h.feedback.mine(TENANT, benUser);
        assertThat(tasks.size()).isEqualTo(1);
        assertThat(tasks.get(0).aboutName()).isEqualTo("Ann Adams");
        assertThat(h.feedback.mine(TENANT, annUser).size()).isEqualTo(0);
        UUID request = tasks.get(0).id();
        assertThatThrownBy(() -> h.feedback.submit(TENANT, request, new FeedbackSubmit(4, "Always helps others settle in.", null), annUser)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> h.feedback.submit(TENANT, request, new FeedbackSubmit(4, "nice", null), benUser)).isInstanceOf(IllegalArgumentException.class);
        FeedbackTask done = h.feedback.submit(TENANT, request, new FeedbackSubmit(4, "Always helps others settle in.", null), benUser);
        assertThat(done.status()).isEqualTo("SUBMITTED");
        assertThatThrownBy(() -> h.feedback.submit(TENANT, request, new FeedbackSubmit(4, "Always helps others settle in.", null), benUser)).isInstanceOf(IllegalStateException.class);
        assertThat(h.feedback.mine(TENANT, benUser).size()).isEqualTo(0);
        FeedbackTask declined = h.feedback.decline(TENANT, h.feedback.mine(TENANT, soloUser).get(0).id(), new ReasonRequest("I do not work with Ann."), soloUser);
        assertThat(declined.status()).isEqualTo("DECLINED");
    }

    @Test
    void theSummaryStaysHiddenUntilThreeHaveRepliedAndNamesStayWithHr() {
        CycleDetail c = openCycle(true);
        UUID id = reviewOf(c, ann);
        UUID extra = h.employee("Thandi", "Tau", boss);
        UUID extraUser = h.signedInAs(extra);
        ask(id, bossUser, NONE, ben, solo, extra);
        h.feedback.submit(TENANT, h.feedback.mine(TENANT, benUser).get(0).id(), new FeedbackSubmit(5, "Always helps others settle in.", "Share status updates earlier."), benUser);
        h.feedback.submit(TENANT, h.feedback.mine(TENANT, soloUser).get(0).id(), new FeedbackSubmit(4, "Calm under pressure at month end.", null), soloUser);
        ReviewDetail two = view(id, bossUser, NONE);
        assertThat(two.feedback().shown()).isFalse();
        assertThat(two.feedback().replies()).isEqualTo(2);
        assertThat(two.feedback().strengths()).isEmpty();
        h.feedback.submit(TENANT, h.feedback.mine(TENANT, extraUser).get(0).id(), new FeedbackSubmit(3, "Reliable with deadlines.", "Delegate more."), extraUser);
        ReviewDetail three = view(id, bossUser, NONE);
        assertThat(three.feedback().shown()).isTrue();
        assertThat(three.feedback().average()).isEqualTo(4.0);
        assertThat(three.feedback().strengths().size()).isEqualTo(3);
        assertThat(three.requests().size()).isEqualTo(3);
        assertThat(three.requests().get(0).providerName()).isNotNull();
        assertThat(three.requests().get(0).rating()).isNull();
        ReviewDetail forHr = view(id, hrUser, HR);
        assertThat(forHr.requests().stream().anyMatch(r -> r.rating() != null && r.strengths() != null)).isTrue();
        ReviewDetail annBefore = view(id, annUser, NONE);
        assertThat(annBefore.feedback()).isNull();
        assertThat(annBefore.requests()).isEmpty();
        h.reviews.submitSelf(TENANT, id, new SelfRequest(4, null), annUser, NONE);
        h.reviews.submitManager(TENANT, id, new ManagerRequest(4, REASONS), bossUser, NONE);
        h.reviews.share(TENANT, id, hrUser, HR);
        ReviewDetail annAfter = view(id, annUser, NONE);
        assertThat(annAfter.feedback().shown()).isTrue();
        assertThat(annAfter.requests()).isEmpty();
    }

    // ── Goals ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anEmployeeProposesAGoalAndTheirManagerApprovesIt() {
        GoalView g = goal(annUser, null, "Cut invoice turnaround");
        assertThat(g.status()).isEqualTo("PROPOSED");
        assertThat(g.employeeName()).isEqualTo("Ann Adams");
        assertThat(g.actions()).containsExactly("EDIT", "CANCEL");
        assertThatThrownBy(() -> h.goals.approve(TENANT, g.id(), annUser, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.goals.approve(TENANT, g.id(), benUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        GoalView approved = h.goals.approve(TENANT, g.id(), bossUser, NONE);
        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(approved.actions()).containsExactly("EDIT", "PROGRESS", "CLOSE", "CANCEL");
        assertThat(h.goals.get(TENANT, g.id(), NONE, annUser).actions()).containsExactly("PROGRESS");
        assertThatThrownBy(() -> h.goals.approve(TENANT, g.id(), bossUser, NONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aManagerOrHrSetsGoalsDirectlyButAStrangerCannot() {
        assertThat(goal(bossUser, ann, "Learn the new ledger").status()).isEqualTo("ACTIVE");
        assertThat(h.goals.create(TENANT, new GoalRequest(ben, null, "Finish onboarding", null, null, today.plusDays(30)), hrUser, HR).status()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> goal(benUser, ann, "Take over Ann's work")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.goals.create(TENANT, new GoalRequest(null, null, "No employee", null, null, today.plusDays(5)), UUID.randomUUID(), NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressIsNotedAndTheManagerClosesTheGoal() {
        GoalView g = goal(bossUser, ann, "Learn the new ledger");
        GoalView p = h.goals.progress(TENANT, g.id(), new ProgressRequest(40, "Finished the first module."), annUser, NONE);
        assertThat(p.progress()).isEqualTo(40);
        assertThat(p.history().get(p.history().size() - 1).note()).contains("40%").contains("first module");
        assertThatThrownBy(() -> h.goals.progress(TENANT, g.id(), new ProgressRequest(140, null), annUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.goals.close(TENANT, g.id(), new GoalCloseRequest("ACHIEVED", null), annUser, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.goals.close(TENANT, g.id(), new GoalCloseRequest("PARTLY_ACHIEVED", null), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        GoalView done = h.goals.close(TENANT, g.id(), new GoalCloseRequest("ACHIEVED", null), bossUser, NONE);
        assertThat(done.status()).isEqualTo("ACHIEVED");
        assertThat(done.progress()).isEqualTo(100);
        assertThat(done.actions()).isEmpty();
        assertThatThrownBy(() -> h.goals.progress(TENANT, g.id(), new ProgressRequest(10, null), annUser, NONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aNewGoalCannotBeDueInThePast() {
        assertThatThrownBy(() -> h.goals.create(TENANT, new GoalRequest(null, null, "Old goal", null, null, today.minusDays(1)), annUser, NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void goalsAreVisibleToTheEmployeeTheirManagerAndHrReaders() {
        GoalView g = goal(annUser, null, "Cut invoice turnaround");
        goal(benUser, null, "Learn the ledger");
        assertThatThrownBy(() -> h.goals.get(TENANT, g.id(), NONE, benUser)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(h.goals.get(TENANT, g.id(), READER, soloUser).actions()).isEmpty();
        assertThat(h.goals.list(TENANT, "mine", null, NONE, annUser).size()).isEqualTo(1);
        assertThat(h.goals.list(TENANT, "team", null, NONE, bossUser).size()).isEqualTo(2);
        assertThat(h.goals.list(TENANT, "team", null, NONE, annUser).size()).isEqualTo(0);
        assertThat(h.goals.list(TENANT, "all", null, READER, soloUser).size()).isEqualTo(2);
        assertThatThrownBy(() -> h.goals.list(TENANT, "all", null, NONE, annUser)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.goals.list(TENANT, "mine", ann, NONE, benUser)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(h.goals.list(TENANT, "mine", ann, NONE, bossUser).size()).isEqualTo(1);
    }

    @Test
    void anEmployeeCanCancelAProposalButNotAnApprovedGoal() {
        GoalView g = goal(annUser, null, "Cut invoice turnaround");
        assertThatThrownBy(() -> h.goals.cancel(TENANT, g.id(), new ReasonRequest(" "), annUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        GoalView cancelled = h.goals.cancel(TENANT, g.id(), new ReasonRequest("No longer relevant."), annUser, NONE);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        GoalView active = goal(bossUser, ann, "Learn the new ledger");
        assertThatThrownBy(() -> h.goals.cancel(TENANT, active.id(), new ReasonRequest("Do not want to."), annUser, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.goals.edit(TENANT, active.id(), new GoalEditRequest("Easier goal", null, null, today.plusDays(10)), annUser, NONE)).isInstanceOf(AccessDeniedException.class);
        assertThat(h.goals.edit(TENANT, active.id(), new GoalEditRequest("Learn the ledger well", null, null, today.plusDays(10)), bossUser, NONE).title()).isEqualTo("Learn the ledger well");
    }

    @Test
    void theReviewShowsTheGoalsOfItsPeriod() {
        CycleDetail c = openCycle(false);
        GoalView inside = h.goals.create(TENANT, new GoalRequest(ann, c.row().id(), "Linked goal", null, null, today.plusDays(20)), bossUser, NONE);
        GoalView other = goal(bossUser, ben, "Ben's goal");
        ReviewDetail d = view(reviewOf(c, ann), annUser, NONE);
        assertThat(d.goals().stream().anyMatch(g -> g.id().equals(inside.id()))).isTrue();
        assertThat(d.goals().stream().noneMatch(g -> g.id().equals(other.id()))).isTrue();
    }

    // ── Improvement plans ─────────────────────────────────────────────────────────────────────────

    @Test
    void anEmployeeSeesAPlanOnlyOnceTheyHaveBeenToldAndCanAcknowledgeIt() {
        PlanDetail d = draftPlan(ann, today, today.plusDays(60), null);
        assertThat(d.row().status()).isEqualTo("DRAFT");
        assertThat(d.row().ownerId()).isEqualTo(boss);
        assertThat(d.actions()).contains("EDIT", "START");
        assertThatThrownBy(() -> h.plans.get(TENANT, d.row().id(), NONE, annUser)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(h.plans.list(TENANT, "mine", null, null, NONE, annUser)).isEmpty();
        assertThatThrownBy(() -> h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today.plusDays(1)), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today), hrUser, READER)).isInstanceOf(AccessDeniedException.class);
        PlanDetail started = h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today), hrUser, HR);
        assertThat(started.row().status()).isEqualTo("ACTIVE");
        assertThat(started.row().communicatedOn()).isEqualTo(today);
        PlanDetail mine = h.plans.get(TENANT, d.row().id(), NONE, annUser);
        assertThat(mine.actions()).containsExactly("ACKNOWLEDGE");
        assertThat(mine.events()).isEmpty();
        assertThat(h.plans.list(TENANT, "mine", null, null, NONE, annUser).size()).isEqualTo(1);
        assertThat(h.plans.acknowledge(TENANT, d.row().id(), new AcknowledgeRequest("I understand."), annUser, NONE).row().acknowledged()).isTrue();
        assertThatThrownBy(() -> h.plans.acknowledge(TENANT, d.row().id(), null, annUser, NONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today), hrUser, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyHrDraftsAPlanAndNeverAboutThemselves() {
        assertThatThrownBy(() -> h.plans.create(TENANT, new PlanRequest(ann, null, null, "T", "x", "y", "z", today, today.plusDays(40)), hrUser, READER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> draftPlan(hr, today, today.plusDays(40), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> h.plans.create(TENANT, new PlanRequest(ann, ann, null, "Reduce invoice errors", "Error rate above five percent.", "Below one percent.", "Weekly coaching.",
                today, today.plusDays(40)), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");
        assertThatThrownBy(() -> draftPlan(ann, today, today.minusDays(1), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOwnerAndHrRecordCheckInsButNobodyElseCan() {
        PlanDetail d = draftPlan(ann, today.minusDays(14), today.plusDays(30), null);
        CheckinRequest req = new CheckinRequest(today, "ON_TRACK", "Reviewed the week's invoices together.");
        assertThatThrownBy(() -> h.plans.checkin(TENANT, d.row().id(), req, bossUser, NONE)).isInstanceOf(IllegalStateException.class);
        h.plans.start(TENANT, d.row().id(), new StartPlanRequest(today.minusDays(14)), hrUser, HR);
        assertThatThrownBy(() -> h.plans.checkin(TENANT, d.row().id(), req, benUser, NONE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> h.plans.checkin(TENANT, d.row().id(), req, annUser, NONE)).isInstanceOf(AccessDeniedException.class);
        PlanDetail after = h.plans.checkin(TENANT, d.row().id(), req, bossUser, NONE);
        assertThat(after.checkins().size()).isEqualTo(1);
        assertThat(after.row().lastProgress()).isEqualTo("ON_TRACK");
        assertThat(h.plans.checkin(TENANT, d.row().id(), new CheckinRequest(today.minusDays(1), "AT_RISK", "Two errors in the last batch."), hrUser, HR).row().lastProgress()).isEqualTo("AT_RISK");
        assertThatThrownBy(() -> h.plans.checkin(TENANT, d.row().id(), new CheckinRequest(today.plusDays(1), "ON_TRACK", "Reviewed the week's invoices."), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.plans.checkin(TENANT, d.row().id(), new CheckinRequest(today.minusDays(30), "ON_TRACK", "Reviewed the week's invoices."), bossUser, NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(h.plans.list(TENANT, "team", null, null, NONE, bossUser).size()).isEqualTo(1);
    }

    @Test
    void aPlanIsNotMetBeforeTheTimeRunsOutOrWithoutARecord() {
        PlanDetail running = h.plans.start(TENANT, draftPlan(ann, today.minusDays(5), today.plusDays(25), null).row().id(), new StartPlanRequest(today.minusDays(5)), hrUser, HR);
        assertThatThrownBy(() -> h.plans.close(TENANT, running.row().id(), new ClosePlanRequest("NOT_MET", "Errors continued throughout."), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("all the time");
        PlanDetail met = h.plans.close(TENANT, running.row().id(), new ClosePlanRequest("MET", "Reached the standard early."), hrUser, HR);
        assertThat(met.row().status()).isEqualTo("MET");
        assertThat(met.closedOn()).isEqualTo(today);
        assertThatThrownBy(() -> h.plans.close(TENANT, running.row().id(), new ClosePlanRequest("MET", "Reached the standard early."), hrUser, HR)).isInstanceOf(IllegalStateException.class);

        PlanDetail over = runningPlan(ben);
        assertThatThrownBy(() -> h.plans.close(TENANT, over.row().id(), new ClosePlanRequest("NOT_MET", "Errors continued throughout."), hrUser, HR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("check-in");
        h.plans.checkin(TENANT, over.row().id(), new CheckinRequest(today.minusDays(20), "OFF_TRACK", "Errors continued in week three."), bossUser, NONE);
        PlanDetail notMet = h.plans.close(TENANT, over.row().id(), new ClosePlanRequest("NOT_MET", "Errors continued throughout the period."), hrUser, HR);
        assertThat(notMet.row().status()).isEqualTo("NOT_MET");
        assertThat(notMet.actions()).isEmpty();
    }

    @Test
    void timeCanBeLengthenedButNeverShortenedAndACancelNeedsAReason() {
        PlanDetail running = h.plans.start(TENANT, draftPlan(ann, today.minusDays(5), today.plusDays(25), null).row().id(), new StartPlanRequest(today.minusDays(5)), hrUser, HR);
        assertThatThrownBy(() -> h.plans.extend(TENANT, running.row().id(), new ExtendRequest(today.plusDays(20), "Shorter please."), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> h.plans.extend(TENANT, running.row().id(), new ExtendRequest(today.plusDays(40), "Sick leave for two weeks."), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
        PlanDetail longer = h.plans.extend(TENANT, running.row().id(), new ExtendRequest(today.plusDays(40), "Sick leave for two weeks."), hrUser, HR);
        assertThat(longer.row().endDate()).isEqualTo(today.plusDays(40));
        assertThatThrownBy(() -> h.plans.cancel(TENANT, running.row().id(), new ReasonRequest(" "), hrUser, HR)).isInstanceOf(IllegalArgumentException.class);
        assertThat(h.plans.cancel(TENANT, running.row().id(), new ReasonRequest("Employee transferred."), hrUser, HR).row().status()).isEqualTo("CANCELLED");
    }

    @Test
    void aShortPlanIsFlaggedAgainstTheMinimumReviewPeriod() {
        assertThat(draftPlan(ann, today, today.plusDays(9), null).row().shorterThanMinimum()).isTrue();
        assertThat(draftPlan(ben, today, today.plusDays(60), null).row().shorterThanMinimum()).isFalse();
    }

    @Test
    void aPlanCanBelongToAPoorPerformanceCaseAboutTheSameEmployee() {
        HrErCase poor = h.poorPerformanceCase(ann, false, UUID.randomUUID());
        PlanDetail d = draftPlan(ann, today, today.plusDays(60), poor.getId());
        assertThat(d.row().caseId()).isEqualTo(poor.getId());
        assertThat(d.row().caseNumber()).isEqualTo("ER-2026-0009");
        Set<String> caseReader = Set.of("HR_ER_READ");
        List<PlanRow> onCase = h.plans.list(TENANT, "mine", null, poor.getId(), caseReader, UUID.randomUUID());
        assertThat(onCase.size()).isEqualTo(1);
        assertThat(h.plans.get(TENANT, d.row().id(), caseReader, UUID.randomUUID()).actions()).isEmpty();
        assertThatThrownBy(() -> h.plans.list(TENANT, "mine", null, poor.getId(), NONE, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> draftPlan(ann, today, today.plusDays(60), h.caseOf("INCAPACITY", "ILL_HEALTH", ann).getId())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("poor performance");
        assertThatThrownBy(() -> draftPlan(ann, today, today.plusDays(60), h.caseOf("DISCIPLINARY", "LATENESS", ann).getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draftPlan(ann, today, today.plusDays(60), h.caseOf("INCAPACITY", "POOR_PERFORMANCE", ben).getId())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different employee");
    }

    @Test
    void aPlanCanBeLinkedToACaseLater() {
        PlanDetail d = draftPlan(ann, today, today.plusDays(60), null);
        HrErCase poor = h.poorPerformanceCase(ann, false, UUID.randomUUID());
        PlanDetail linked = h.plans.linkCase(TENANT, d.row().id(), new CaseLinkRequest(poor.getId()), hrUser, HR);
        assertThat(linked.row().caseNumber()).isEqualTo("ER-2026-0009");
        assertThatThrownBy(() -> h.plans.linkCase(TENANT, d.row().id(), new CaseLinkRequest(poor.getId()), bossUser, NONE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aConfidentialCasesPlanStaysWithTheCasesPeople() {
        UUID officer = UUID.randomUUID();
        HrErCase secret = h.poorPerformanceCase(ann, true, officer);
        Set<String> withSensitive = Set.of("HR_PERF_READ", "HR_PERF_MANAGE", "HR_ER_READ", "HR_ER_MANAGE", "HR_SENSITIVE_READ");
        assertThatThrownBy(() -> draftPlan(ann, today, today.plusDays(60), secret.getId())).isInstanceOf(ResourceNotFoundException.class);
        PlanDetail d = h.plans.create(TENANT, new PlanRequest(ann, null, secret.getId(), "Reduce invoice errors", "Error rate above five percent.", "Below one percent.",
                "Weekly coaching and a checklist.", today, today.plusDays(60)), hrUser, withSensitive);
        assertThat(h.plans.list(TENANT, "all", null, null, READER, soloUser)).isEmpty();
        assertThatThrownBy(() -> h.plans.get(TENANT, d.row().id(), READER, soloUser)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(h.plans.list(TENANT, "all", null, null, withSensitive, hrUser).size()).isEqualTo(1);
        assertThat(h.plans.get(TENANT, d.row().id(), NONE, bossUser).row().id()).isEqualTo(d.row().id());
    }

    @Test
    void theAuditTrailRecordsWhatWasDone() {
        CycleDetail c = openCycle(false);
        UUID id = annSubmitted(c);
        h.reviews.share(TENANT, id, hrUser, HR);
        List<String> actions = h.eventStore.stream().filter(e -> e.getRefId().equals(id)).map(e -> e.getAction()).toList();
        assertThat(actions).containsExactly("SELF_SUBMITTED", "MANAGER_SUBMITTED", "SHARED");
    }
}
