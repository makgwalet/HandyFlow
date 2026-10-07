package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord.ScreeningReason;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord.ScreeningResult;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord.ScreeningType;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardScreeningRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.DecideScreeningRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Sign-off rules on a screening: needs a result, a reason when not clearing, tenant and guard scoping. */
@ExtendWith(MockitoExtension.class)
class GuardScreeningServiceTest {

    @Mock private GuardScreeningRepository screeningRepository;
    @Mock private GuardRepository guardRepository;
    @Mock private SiteRepository siteRepository;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private final UUID guardId = UUID.randomUUID();

    private GuardScreeningService service() { return new GuardScreeningService(screeningRepository, guardRepository, siteRepository); }

    private GuardScreeningRecord record(boolean withResult) {
        var r = GuardScreeningRecord.create(TENANT, guardId, ScreeningType.CRIMINAL_RECORD_CHECK, ScreeningReason.ONBOARDING, USER);
        if (withResult) r.recordResult(ScreeningResult.PASS, "ABC", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 10, 5), "CR-1", null);
        lenient().when(screeningRepository.findById(r.getId())).thenReturn(Optional.of(r));
        lenient().when(screeningRepository.findByGuard(TENANT, guardId)).thenReturn(List.of(r));
        return r;
    }

    @Test @DisplayName("Clearing a screening with a result records who signed off and when")
    void clears() {
        var r = record(true);
        service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("cleared", " Looks good "), USER, "Security Manager");
        assertThat(r.getDecision()).isEqualTo("CLEARED");
        assertThat(r.getDecisionNote()).isEqualTo("Looks good");
        assertThat(r.getDecidedByName()).isEqualTo("Security Manager");
        assertThat(r.getDecidedAt()).isNotNull();
    }

    @Test @DisplayName("A screening still pending cannot be signed off")
    void pendingRefused() {
        var r = record(false);
        assertThatThrownBy(() -> service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("CLEARED", null), USER, "x"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Record the result");
    }

    @Test @DisplayName("Not clearing needs a reason")
    void notClearedNeedsNote() {
        var r = record(true);
        assertThatThrownBy(() -> service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("NOT_CLEARED", "  "), USER, "x"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("reason");
        assertThat(r.getDecision()).isNull();
    }

    @Test @DisplayName("Only CLEARED or NOT_CLEARED are accepted")
    void badDecision() {
        var r = record(true);
        assertThatThrownBy(() -> service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("MAYBE", null), USER, "x"))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("A NOT_CLEARED decision counts as a failed screening")
    void notClearedIsFailed() {
        var r = record(true);
        service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("NOT_CLEARED", "Reference was false"), USER, "x");
        assertThat(r.isFailed()).isTrue();
    }

    @Test @DisplayName("Recording a new result wipes the earlier sign-off")
    void newResultClearsDecision() {
        var r = record(true);
        service().decide(TENANT, guardId, r.getId(), new DecideScreeningRequest("CLEARED", null), USER, "x");
        r.recordResult(ScreeningResult.FAIL, "ABC", LocalDate.of(2026, 10, 6), null, null, null);
        assertThat(r.getDecision()).isNull();
        assertThat(r.getDecidedByName()).isNull();
    }

    @Test @DisplayName("A record from another guard or tenant is not found")
    void scoping() {
        var r = record(true);
        assertThatThrownBy(() -> service().decide(TENANT, UUID.randomUUID(), r.getId(), new DecideScreeningRequest("CLEARED", null), USER, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().decide(TenantId.generate(), guardId, r.getId(), new DecideScreeningRequest("CLEARED", null), USER, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
