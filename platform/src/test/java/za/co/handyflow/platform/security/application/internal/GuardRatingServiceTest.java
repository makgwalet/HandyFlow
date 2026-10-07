package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.security.domain.model.GuardRating;
import za.co.handyflow.platform.security.domain.repository.GuardRatingRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRatingRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ratings: each score 1 to 5, a known source, no future dates, and the average of the six. */
@ExtendWith(MockitoExtension.class)
class GuardRatingServiceTest {

    @Mock private GuardRatingRepository repository;
    @Mock private GuardService guardService;
    @Mock private SiteRepository siteRepository;

    private static final TenantId TENANT = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final UUID guardId = UUID.randomUUID();

    private GuardRatingService service() { return new GuardRatingService(repository, guardService, siteRepository); }
    private static SaveRatingRequest req(String source, LocalDate on, int p, int pr, int a, int c, int al, int ih) {
        return new SaveRatingRequest(null, source, "Mrs Dlamini", on, p, pr, a, c, al, ih, "Good");
    }

    @Test @DisplayName("Records a rating and reports the average of the six scores")
    void records() {
        when(repository.save(any(GuardRating.class))).thenAnswer(i -> i.getArgument(0));
        var out = service().add(TENANT, guardId, req("client", TODAY, 5, 4, 5, 4, 3, 5), UUID.randomUUID(), "Sam", TODAY);
        assertThat(out.source()).isEqualTo("CLIENT");
        assertThat(out.average()).isEqualTo(4.3);
        assertThat(out.raterName()).isEqualTo("Mrs Dlamini");
    }

    @Test @DisplayName("Rejects scores outside 1 to 5, unknown sources and future dates")
    void validation() {
        assertThatThrownBy(() -> service().add(TENANT, guardId, req("CLIENT", TODAY, 0, 4, 5, 4, 3, 5), null, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("1 to 5");
        assertThatThrownBy(() -> service().add(TENANT, guardId, req("CLIENT", TODAY, 5, 4, 5, 4, 3, 6), null, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("1 to 5");
        assertThatThrownBy(() -> service().add(TENANT, guardId, req("MANAGER", TODAY, 5, 4, 5, 4, 3, 5), null, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("source");
        assertThatThrownBy(() -> service().add(TENANT, guardId, req("CLIENT", TODAY.plusDays(1), 5, 4, 5, 4, 3, 5), null, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("future");
        verify(repository, never()).save(any());
    }
}
