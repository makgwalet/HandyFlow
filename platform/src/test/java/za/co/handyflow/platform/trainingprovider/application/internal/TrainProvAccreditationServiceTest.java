package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAccreditationServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID MAKER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvDeliverySiteRepository sites;
    @Mock TrainProvAccreditationRepository accreditations;
    @Mock TrainProvAccreditationScopeRepository scopes;
    @Mock TrainProvAccreditationEventRepository events;
    @Mock TrainProvCourseRepository courses;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeOfferingRepository offerings;
    @InjectMocks TrainProvAccreditationService service;

    private TrainProvAccreditation underReview() {
        TrainProvAccreditation a = TrainProvAccreditation.create(TENANT, UUID.randomUUID(), "TEST accreditation", "ACC-1", MAKER);
        a.applyTransition("UNDER_REVIEW", null, null, null, null, null, null, null, MAKER, TODAY);
        return a;
    }

    private TransitionRequest approve(String source) {
        return new TransitionRequest("APPROVED", null, "ACC-1", TODAY, TODAY.plusYears(5), null, source, TODAY, null);
    }

    @Test
    void onlyAnAdministratorCanRecordAnApproval() {
        TrainProvAccreditation a = underReview();
        when(accreditations.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(TENANT, a.getId(), approve("Authority letter"), CHECKER, false, TODAY))
                .isInstanceOf(HandyFlowException.class);
        verify(accreditations, never()).save(any());
    }

    @Test
    void approvalWithoutAVerificationSourceIsRefused() {
        TrainProvAccreditation a = underReview();
        when(accreditations.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(TENANT, a.getId(), approve(""), CHECKER, true, TODAY))
                .isInstanceOf(HandyFlowException.class);
        assertThat(a.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approvalByASecondPersonIsRecordedWithHistory() {
        TrainProvAccreditation a = underReview();
        when(accreditations.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(authorities.findAllForTenant(TENANT)).thenReturn(List.of());
        when(scopes.findForAccreditation(TENANT, a.getId())).thenReturn(List.of());

        AccreditationView v = service.transition(TENANT, a.getId(), approve("Authority letter 2026"), CHECKER, true, TODAY);

        assertThat(v.status()).isEqualTo("APPROVED");
        assertThat(a.getVerifiedBy()).isEqualTo(CHECKER);
        verify(events).save(any(TrainProvAccreditationEvent.class));
    }

    @Test
    void theSamePersonNeedsAReasonToApproveTheirOwnEntry() {
        TrainProvAccreditation a = underReview();
        when(accreditations.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(TENANT, a.getId(), approve("Authority letter 2026"), MAKER, true, TODAY))
                .isInstanceOf(HandyFlowException.class);
        assertThat(a.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void anAuthorityWithLiveAccreditationsCannotBeRetired() {
        TrainProvAuthority auth = TrainProvAuthority.create(TENANT, "TEST authority", "SETA", null);
        when(authorities.findForTenant(TENANT, auth.getId())).thenReturn(Optional.of(auth));
        when(accreditations.countLiveForAuthority(TENANT, auth.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.retireAuthority(TENANT, auth.getId(), "No longer used here", MAKER))
                .isInstanceOf(HandyFlowException.class);
        assertThat(auth.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void aSiteNamedInLiveScopeCannotBeRetired() {
        TrainProvDeliverySite site = TrainProvDeliverySite.create(TENANT, "TEST campus", "CAMPUS", null, null, null, null);
        when(sites.findForTenant(TENANT, site.getId())).thenReturn(Optional.of(site));
        when(scopes.countLiveForSite(TENANT, site.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.retireSite(TENANT, site.getId(), "Campus closed down", MAKER))
                .isInstanceOf(HandyFlowException.class);
        assertThat(site.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void scopeCannotBeAddedAtARetiredSite() {
        TrainProvAccreditation a = underReview();
        TrainProvDeliverySite site = TrainProvDeliverySite.create(TENANT, "TEST campus", "CAMPUS", null, null, null, null);
        site.retire("Campus closed down", MAKER);
        when(accreditations.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(sites.findForTenant(TENANT, site.getId())).thenReturn(Optional.of(site));

        assertThatThrownBy(() -> service.addScope(TENANT, a.getId(),
                new ScopeRequest(site.getId(), null, null, "TEST programme", null, null, null, null, null, null), MAKER))
                .isInstanceOf(HandyFlowException.class);
        verify(scopes, never()).save(any());
    }

    @Test
    void aProgrammeWithNoScopeIsNotCovered() {
        UUID courseId = UUID.randomUUID();
        TrainProvDeliverySite site = TrainProvDeliverySite.create(TENANT, "TEST campus", "CAMPUS", null, null, null, null);
        when(courses.findActiveById(TENANT, courseId)).thenReturn(Optional.of(mock(TrainProvCourse.class)));
        when(sites.findForTenant(TENANT, site.getId())).thenReturn(Optional.of(site));
        when(scopes.findForCourse(TENANT, courseId)).thenReturn(List.of());

        CoverageView c = service.coverage(TENANT, courseId, site.getId(), TODAY);

        assertThat(c.covered()).isFalse();
        assertThat(c.outcome()).isEqualTo("NO_SCOPE");
    }
}
