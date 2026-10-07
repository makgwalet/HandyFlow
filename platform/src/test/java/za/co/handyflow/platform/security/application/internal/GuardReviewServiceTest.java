package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.GuardReview;
import za.co.handyflow.platform.security.domain.repository.GuardReviewRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RatingItem;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRatingRequest;
import za.co.handyflow.platform.security.dto.GuardReviewDtos.SaveReviewRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Supervisor reviews: validation, the matching rating, and when the next review is due. */
class GuardReviewServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private final GuardReviewRepository repository = mock(GuardReviewRepository.class);
    private final GuardService guardService = mock(GuardService.class);
    private final GuardRatingService ratingService = mock(GuardRatingService.class);
    private final SiteRepository siteRepository = mock(SiteRepository.class);
    private final GuardReviewService service = new GuardReviewService(repository, guardService, ratingService, siteRepository);
    private final UUID guardId = UUID.randomUUID();

    private SaveReviewRequest req(String overall, LocalDate from, LocalDate to, LocalDate on, String strengths, String improvements, LocalDate followUp, int score) {
        return new SaveReviewRequest(null, from, to, on, overall, score, score, score, score, score, score, strengths, improvements, "First aid refresher", "Daily log checks", followUp);
    }

    private SaveReviewRequest good() { return req("meets", TODAY.minusDays(90), TODAY.minusDays(1), TODAY, "Reliable at the gate", null, TODAY.plusDays(30), 4); }

    private void stubSaves() {
        when(ratingService.add(any(), any(), any(SaveRatingRequest.class), any(), any()))
                .thenReturn(new RatingItem(UUID.randomUUID(), "SUPERVISOR", "Sam", null, null, TODAY, 4, 4, 4, 4, 4, 4, 4.0, null, "Sam", null));
        when(repository.save(any(GuardReview.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test @DisplayName("Saving a review records a matching supervisor rating and links the two")
    void savesWithRating() {
        stubSaves();
        var out = service.add(TENANT, guardId, good(), USER, "Sam", TODAY);
        assertThat(out.overall()).isEqualTo("MEETS");
        assertThat(out.average()).isEqualTo(4.0);
        assertThat(out.reviewerName()).isEqualTo("Sam");
        var captor = org.mockito.ArgumentCaptor.forClass(SaveRatingRequest.class);
        verify(ratingService).add(any(), any(), captor.capture(), any(), any());
        assertThat(captor.getValue().source()).isEqualTo("SUPERVISOR");
        assertThat(captor.getValue().ratedOn()).isEqualTo(TODAY);
        assertThat(captor.getValue().comment()).startsWith("Supervisor review: Meets expectations.").contains("Reliable at the gate");
        var saved = org.mockito.ArgumentCaptor.forClass(GuardReview.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRatingId()).isNotNull();
    }

    @Test @DisplayName("Bad input is refused before anything is saved")
    void validation() {
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("GREAT", TODAY.minusDays(9), TODAY, TODAY, "x", null, null, 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("overall");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY, TODAY.minusDays(1), TODAY, "x", null, null, 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("period");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY.minusDays(9), TODAY.plusDays(1), TODAY.plusDays(1), "x", null, null, 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY.minusDays(9), TODAY, TODAY.minusDays(2), "x", null, null, 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("before the end");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY.minusDays(9), TODAY, TODAY, "x", null, TODAY.minusDays(1), 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("follow-up");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY.minusDays(9), TODAY, TODAY, " ", null, null, 4), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("went well");
        assertThatThrownBy(() -> service.add(TENANT, guardId, req("MEETS", TODAY.minusDays(9), TODAY, TODAY, "x", null, null, 6), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("1 to 5");
        assertThatThrownBy(() -> service.add(TENANT, guardId, good(), USER, " ", TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("reviewer");
        verify(repository, never()).save(any());
        verify(ratingService, never()).add(any(), any(), any(), any(), any());
    }

    @Test @DisplayName("A guard is due a review when never reviewed, when a follow-up has arrived, or after 90 days")
    void dueState() {
        assertThat(GuardReviewRules.dueState(null, null, TODAY)).isEqualTo("NONE");
        assertThat(GuardReviewRules.dueState(TODAY.minusDays(10), TODAY.minusDays(1), TODAY)).isEqualTo("FOLLOW_UP_DUE");
        assertThat(GuardReviewRules.dueState(TODAY.minusDays(10), TODAY.plusDays(20), TODAY)).isEqualTo("OK");
        assertThat(GuardReviewRules.dueState(TODAY.minusDays(90), null, TODAY)).isEqualTo("OK");
        assertThat(GuardReviewRules.dueState(TODAY.minusDays(91), null, TODAY)).isEqualTo("OVERDUE");
    }

    @Test @DisplayName("The list reports the last review, days since and the due state")
    void list() {
        stubSaves();
        var r = GuardReview.create(TENANT, guardId, null, TODAY.minusDays(100), TODAY.minusDays(190), TODAY.minusDays(101), "Sam", GuardReview.Overall.BELOW,
                new int[]{2, 2, 3, 3, 2, 2}, null, "Late often", null, null, null, UUID.randomUUID(), USER, "Sam");
        when(repository.findForGuard(TENANT, guardId)).thenReturn(List.of(r));
        var out = service.list(TENANT, guardId, TODAY);
        assertThat(out.reviews()).hasSize(1);
        assertThat(out.daysSinceLast()).isEqualTo(100);
        assertThat(out.dueState()).isEqualTo("OVERDUE");
        assertThat(out.intervalDays()).isEqualTo(90);
        when(repository.findForGuard(TENANT, guardId)).thenReturn(List.of());
        assertThat(service.list(TENANT, guardId, TODAY).dueState()).isEqualTo("NONE");
    }
}
